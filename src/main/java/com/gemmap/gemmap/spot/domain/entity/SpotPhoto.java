package com.gemmap.gemmap.spot.domain.entity;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "spot_photos")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // 사진 id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private Spot spot; // 스팟 id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 사진 등록자 (사용자 id)

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl; // 오브젝트 스토리지 URL

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ESpotPhotoType type; // 업로드 목적

    @Column(name = "taken_at")
    private LocalDateTime takenAt; // 촬영 시각 (KST 저장)

    @Column(precision = 10, scale = 8, nullable = false)
    private BigDecimal latitude; // 위도

    @Column(precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude; // 경도

    @Column(name = "camera_make", length = 100)
    private String cameraMake; // 카메라 브랜드

    @Column(name = "camera_model", length = 100)
    private String cameraModel; // 카메라 모델

    @Column(precision = 5, scale = 2)
    private BigDecimal focalLength; // 초점거리(mm)

    @Column(precision = 4, scale = 2)
    private BigDecimal aperture; // 조리개값(F-number)

    @Column(name = "iso")
    private Integer iso; // ISO 감도

    @Column(name = "shutter_speed", length = 20)
    private String shutterSpeed; // 셔터속도 (sec)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 생성시각
}
