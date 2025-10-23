package com.gemmap.spot.image.presentation;

import com.gemmap.common.annotation.UserId;
import com.gemmap.spot.image.application.dto.response.ImageUploadResponse;
import com.gemmap.spot.image.domain.entity.Image;
import com.gemmap.spot.image.application.service.ImageService;
import com.gemmap.common.dto.ResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 이미지 업로드/삭제 API
 * - JWT 인증 필수 (SecurityConfig에서 /api/v1/images/** 경로는 USER, ADMIN 권한 필요)
 * - @UserId 어노테이션으로 JWT에서 사용자 ID 추출
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 이미지 업로드
     *
     * @param userId JWT 토큰에서 추출한 인증된 사용자 ID
     * @param files 업로드할 이미지 파일 목록
     * @return 업로드된 이미지 정보 목록
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<List<ImageUploadResponse>> uploadImages(
            @UserId Long userId,
            @RequestParam("files") List<MultipartFile> files) {
        log.info("Image upload request from user: {}, file count: {}", userId, files.size());

        List<Image> images = imageService.uploadImages(files);
        List<ImageUploadResponse> response = images.stream()
                .map(ImageUploadResponse::new)
                .toList();
        return ResponseDto.created(response);
    }

    /**
     * 지정된 URL의 이미지를 삭제합니다.
     *
     * @param userId JWT 토큰에서 추출한 인증된 사용자 ID
     * @param fileUrl 삭제할 이미지의 전체 URL
     * @return ResponseDto
     */
    @DeleteMapping
    public ResponseDto<?> deleteImage(
            @UserId Long userId,
            @RequestParam String fileUrl) {
        log.info("Image delete request from user: {}, fileUrl: {}", userId, fileUrl);

        imageService.deleteImage(fileUrl);
        return ResponseDto.noContent();
    }
}
