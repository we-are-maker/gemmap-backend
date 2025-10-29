package com.gemmap.gemmap.spot.application.dto.response;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Builder
public record SpotDetailResponse(
        Long spotId,            // 스팟 ID
        String profileImage,    // 사용자 프로필 이미지
        String nickname,        // 사용자 닉네임
        String alias,           // 스팟 별칭
        String fileUrl,         // 사진 URL
        String address,         // 도로명주소
        String takenAt,         // 촬영시각(UTC, ISO-8601 Z)
        BigDecimal latitude,    // 위도
        BigDecimal longitude,   // 경도
        String cameraMake,      // 카메라 브랜드
        String cameraModel,     // 카메라 모델
        BigDecimal aperture,    // 조리개
        String shutterSpeed,    // 셔터속도
        Integer iso,            // ISO
        BigDecimal focalLength, // 초점거리
        String createdAt        // 생성시각(스팟, UTC, ISO-8601 Z)
) {

    public static SpotDetailResponse from (Spot spot, User spotOwner, SpotPhoto photo) {
        return SpotDetailResponse.builder()
                .spotId(spot.getId())
                .profileImage(spotOwner.getProfileImage())
                .nickname(spotOwner.getNickname())
                .alias(spot.getAlias())
                .fileUrl(photo.getFileUrl())
                .address(spot.getAddress())
                .takenAt(toUtcIso(photo.getTakenAt())) // LocalDateTime/Instant/OffsetDateTime 대응
                .latitude(photo.getLatitude())
                .longitude(photo.getLongitude())
                .cameraMake(photo.getCameraMake())
                .cameraModel(photo.getCameraModel())
                .aperture(photo.getAperture())
                .shutterSpeed(photo.getShutterSpeed())
                .iso(photo.getIso())
                .focalLength(photo.getFocalLength())
                .createdAt(toUtcIso(spot.getCreatedAt()))
                .build();
    }

    // 시간 포맷터 (UTC ISO_INSTANT)
    private static String toUtcIso(Object temporal) {
        if (temporal == null) return null;
        Instant instant;

        if (temporal instanceof Instant i) {
            instant = i;
        } else if (temporal instanceof LocalDateTime ldt) {
            instant = ldt.atZone(ZoneId.systemDefault()).toInstant();
        } else if (temporal instanceof OffsetDateTime odt) {
            instant = odt.toInstant();
        } else if (temporal instanceof ZonedDateTime zdt) {
            instant = zdt.toInstant();
        } else {
            throw new IllegalArgumentException("Unsupported temporal type: " + temporal.getClass());
        }

        instant = instant.truncatedTo(ChronoUnit.SECONDS);
        return DateTimeFormatter.ISO_INSTANT.format(instant); // ex) 2025-10-23T11:22:33Z
    }
}
