package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.image.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.image.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.dto.response.SpotCreateResponse;
import com.gemmap.gemmap.spot.application.support.SpotFileValidator;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotService {

    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final ObjectStorageService objectStorageService;
    private final S3UrlGenerator s3UrlGenerator;
    private final S3Properties s3Properties;
    private final SpotFileValidator spotFileValidator;

    @Transactional
    public SpotCreateResponse create(Long userId, SpotCreateRequest req, MultipartFile file) {
        // 0) 요청 검증 (빠른 실패 우선: 좌표 검증 -> 파일 검증)
        validateCoordinates(req.latitude(), req.longitude());
        spotFileValidator.validateImage(file);

        // 1) 업로드 (키 규칙: spots/yyyy/MM/uuid.ext)
        String key = buildSpotKey(file.getOriginalFilename());
        String fileUrl;
        try {
            // 기존 ObjectStorageService 사용
            objectStorageService.upload(s3Properties.getBucket(), key, file);
            // URL 생성 (NHN Cloud 형식)
            fileUrl = s3UrlGenerator.generateUrl(key);
        } catch (Exception e) {
            log.error("S3 upload failed for key: {}", key, e);
            throw new CommonException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        try {
            // 2) spots 저장
            Spot spot = spotRepository.save(
                Spot.builder()
                    .userId(userId)
                    .address(req.address())
                    .alias(req.alias())
                    .build()
            );

            // 3) spot_photos 저장 (type=SPOT, fileUrl 직접 저장)
            SpotPhoto photo = SpotPhoto.builder()
                .spot(spot)
                .fileUrl(fileUrl)
                .type(SpotPhoto.Type.SPOT)
                .takenAt(parseUtc(req.takenAt()))
                .latitude(req.latitude())
                .longitude(req.longitude())
                .cameraMake(req.cameraMake())
                .cameraModel(req.cameraModel())
                .focalLength(req.focalLength())
                .aperture(req.aperture())
                .iso(req.iso())
                .shutterSpeed(req.shutterSpeed())
                .build();
            spotPhotoRepository.save(photo);

            // 4) 응답
            return SpotCreateResponse.builder()
                .spotId(spot.getId())
                .fileUrl(fileUrl)
                .build();
        } catch (RuntimeException ex) {
            // 보상 트랜잭션: DB 저장 실패 시 업로드된 S3 객체 삭제
            safeDeleteObject(key);
            throw ex;
        }
    }

    private void validateCoordinates(BigDecimal lat, BigDecimal lon) {
        if (lat != null && (lat.compareTo(BigDecimal.valueOf(-90)) < 0
                || lat.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "위도는 -90~90 범위여야 합니다.");
        }
        if (lon != null && (lon.compareTo(BigDecimal.valueOf(-180)) < 0
                || lon.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "경도는 -180~180 범위여야 합니다.");
        }
    }

    private static LocalDateTime parseUtc(String isoZ) {
        if (isoZ == null || isoZ.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(isoZ), ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "takenAt은 UTC ISO8601 형식이어야 합니다.");
        }
    }

    private String buildSpotKey(String originalName) {
        String ext = Optional.ofNullable(originalName)
            .filter(n -> n.contains("."))
            .map(n -> n.substring(n.lastIndexOf('.')))
            .orElse(".jpg");
        String ym = DateTimeFormatter.ofPattern("yyyy/MM").format(LocalDate.now(ZoneOffset.UTC));
        return "spots/" + ym + "/" + UUID.randomUUID() + ext;
    }

    private void safeDeleteObject(String key) {
        try {
            objectStorageService.delete(s3Properties.getBucket(), key);
            log.info("Compensated: deleted S3 object {}", key);
        } catch (Exception e) {
            log.warn("Failed to delete S3 object {} during compensation", key, e);
        }
    }
}
