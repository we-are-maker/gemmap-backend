package com.gemmap.gemmap.image.presentation;

import com.gemmap.gemmap.image.application.dto.response.ImageUploadResponse;
import com.gemmap.gemmap.image.domain.entity.Image;
import com.gemmap.gemmap.image.application.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageUploadResponse>> uploadImages(@RequestParam("files") List<MultipartFile> files) {
        List<Image> images = imageService.uploadImages(files);
        List<ImageUploadResponse> response = images.stream()
                .map(ImageUploadResponse::new)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 지정된 URL의 이미지를 삭제합니다.
     *
     * @param fileUrl 삭제할 이미지의 전체 URL
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteImage(@RequestParam String fileUrl) {
        imageService.deleteImage(fileUrl);
        return ResponseEntity.noContent().build();
    }
}
