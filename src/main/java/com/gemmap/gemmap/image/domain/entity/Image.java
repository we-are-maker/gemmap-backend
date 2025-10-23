package com.gemmap.gemmap.image.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "image")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false)
    private StorageProvider storageProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ImageStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "camera_make", length = 100)
    private String cameraMake;

    @Column(name = "camera_model", length = 100)
    private String cameraModel;

    @Column(name = "focal_length", precision = 5, scale = 2)
    private BigDecimal focalLength;

    @Column(name = "aperture", precision = 4, scale = 2)
    private BigDecimal aperture;

    @Column(name = "iso")
    private Integer iso;

    @Column(name = "shutter_speed", length = 20)
    private String shutterSpeed;


    @Builder
    public Image(String fileUrl, Integer width, Integer height, String mimeType, Long sizeBytes, LocalDateTime takenAt, BigDecimal latitude, BigDecimal longitude, String cameraMake, String cameraModel, BigDecimal focalLength, BigDecimal aperture, Integer iso, String shutterSpeed) {
        this.fileUrl = fileUrl;
        this.width = width;
        this.height = height;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.storageProvider = StorageProvider.NHN_OBJECT;
        this.status = ImageStatus.ACTIVE;
        this.takenAt = takenAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cameraMake = cameraMake;
        this.cameraModel = cameraModel;
        this.focalLength = focalLength;
        this.aperture = aperture;
        this.iso = iso;
        this.shutterSpeed = shutterSpeed;
    }
}