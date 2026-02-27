package com.gemmap.gemmap.image.application.service;

import com.gemmap.gemmap.image.domain.entity.Image;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.image.domain.repository.ImageRepository;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.shared.util.S3FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageRepository imageRepository;
    private final ObjectStorageService objectStorageService;
    private final S3Properties s3Properties;
    private final S3UrlGenerator s3UrlGenerator;

    @Value("${s3.spot-base-path}")
    private String spotBasePath;

    private static final List<String> ALLOWED_MIME_TYPES = List.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Transactional
    public List<Image> uploadImages(List<MultipartFile> files) {
        validateFiles(files);

        return files.stream()
                .map(this::uploadSingleImage)
                .toList();
    }

    private Image uploadSingleImage(MultipartFile file) {
        validate(file);

        String key = S3FileUtils.buildKey(spotBasePath, file.getOriginalFilename());

        // 1. Object Storage에 업로드
        objectStorageService.upload(s3Properties.getBucket(), key, file);

        try {
            // 2. URL 생성 및 DB 저장
            String fileUrl = s3UrlGenerator.generateUrl(key);
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            int width = (bufferedImage != null) ? bufferedImage.getWidth() : 0;
            int height = (bufferedImage != null) ? bufferedImage.getHeight() : 0;

            Image.ImageBuilder imageBuilder = Image.builder()
                    .fileUrl(fileUrl)
                    .width(width)
                    .height(height)
                    .mimeType(file.getContentType())
                    .sizeBytes(file.getSize());

            // EXIF 데이터 추출
            ExifExtractor.extract(file, imageBuilder);

            Image image = imageBuilder.build();

            return imageRepository.save(image);

        } catch (Exception e) {
            // 3. DB 저장 실패 시 업로드된 S3 객체 삭제 (보상 트랜잭션)
            log.error("Failed to save image metadata to DB. Deleting uploaded object from S3.", e);
            objectStorageService.delete(s3Properties.getBucket(), key);
            throw new CommonException(ErrorCode.DATABASE_ERROR, "이미지 정보 저장 중 오류가 발생했습니다.");
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "하나 이상의 파일을 업로드해야 합니다.");
        }
        if (files.size() > 5) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "한 번에 최대 5개의 파일만 업로드할 수 있습니다.");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "파일이 비어있습니다.");
        }

        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new CommonException(ErrorCode.INVALID_FILE_FORMAT, "지원하지 않는 이미지 형식입니다: " + file.getContentType());
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CommonException(ErrorCode.FILE_SIZE_EXCEEDED, "파일 크기가 10MB를 초과할 수 없습니다.");
        }
    }

    /**
     * Object Storage와 데이터베이스에서 이미지를 삭제합니다.
     *
     * @param fileUrl 삭제할 이미지의 전체 URL
     */
    @Transactional
    public void deleteImage(String fileUrl) {
        // 1. URL에서 Object Storage 키 추출
        String key = s3UrlGenerator.extractKeyFromUrl(fileUrl);
        log.info("Attempting to delete image. URL: {}, Key: {}", fileUrl, key);

        try {
            // 2. Object Storage에서 파일 삭제
            objectStorageService.delete(s3Properties.getBucket(), key);

            // 3. 데이터베이스에서 이미지 메타데이터 삭제
            imageRepository.findByFileUrl(fileUrl).ifPresent(image -> {
                log.info("Deleting image metadata from database. Image ID: {}", image.getId());
                imageRepository.delete(image);
            });

        } catch (CommonException e) {
            // S3 삭제 실패 시
            log.error("Failed to delete image from Object Storage. URL: {}", fileUrl, e);
            throw e; // 예외를 그대로 다시 던짐
        } catch (Exception e) {
            // 기타 예외 (DB 등)
            log.error("An unexpected error occurred while deleting image. URL: {}", fileUrl, e);
            throw new CommonException(ErrorCode.IMAGE_DELETE_FAILED);
        }
    }
}