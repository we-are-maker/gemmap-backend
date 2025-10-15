package com.gemmap.gemmap.image.application.dto.response;

import com.gemmap.gemmap.image.domain.entity.Image;
import com.gemmap.gemmap.image.domain.entity.ImageStatus;
import com.gemmap.gemmap.image.domain.entity.StorageProvider;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ImageUploadResponse {

    private final Long id;
    private final String fileUrl;
    private final Integer width;
    private final Integer height;
    private final String mimeType;
    private final Long sizeBytes;
    private final StorageProvider storageProvider;
    private final ImageStatus status;
    private final LocalDateTime createdAt;

    public ImageUploadResponse(Image image) {
        this.id = image.getId();
        this.fileUrl = image.getFileUrl();
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.mimeType = image.getMimeType();
        this.sizeBytes = image.getSizeBytes();
        this.storageProvider = image.getStorageProvider();
        this.status = image.getStatus();
        this.createdAt = image.getCreatedAt();
    }
}
