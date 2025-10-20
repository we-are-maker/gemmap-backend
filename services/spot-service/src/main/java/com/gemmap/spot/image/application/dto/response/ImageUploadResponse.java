package com.gemmap.spot.image.application.dto.response;

import com.gemmap.spot.image.domain.entity.Image;
import com.gemmap.spot.image.domain.entity.ImageStatus;
import com.gemmap.spot.image.domain.entity.StorageProvider;
import lombok.Getter;


import java.math.BigDecimal;
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
    private final LocalDateTime takenAt;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String cameraMake;
    private final String cameraModel;
    private final BigDecimal focalLength;
    private final BigDecimal aperture;
    private final Integer iso;
    private final String shutterSpeed;

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
        this.takenAt = image.getTakenAt();
        this.latitude = image.getLatitude();
        this.longitude = image.getLongitude();
        this.cameraMake = image.getCameraMake();
        this.cameraModel = image.getCameraModel();
        this.focalLength = image.getFocalLength();
        this.aperture = image.getAperture();
        this.iso = image.getIso();
        this.shutterSpeed = image.getShutterSpeed();
    }
}
