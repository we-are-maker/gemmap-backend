package com.gemmap.gemmap.spot.application.dto.response;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.EAttractionLevel;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Builder
public record SpotDetailResponse(
        Long spotId,                      // 스팟 ID
        String profileImage,              // 사용자 프로필 이미지
        String nickname,                  // 사용자 닉네임
        String alias,                     // 스팟 별칭
        String fileUrl,                   // 사진 URL
        String address,                   // 전체 주소
        EAttractionLevel attractionLevel, // 나의 평가 (끌림지수), null이면 찜하기 하지 않은 경우
        String takenAt,                   // 촬영시각(KST, ISO-8601)
        BigDecimal latitude,              // 위도
        BigDecimal longitude,             // 경도
        String cameraMake,                // 카메라 브랜드
        String cameraModel,               // 카메라 모델
        BigDecimal aperture,              // 조리개
        String shutterSpeed,              // 셔터속도
        Integer iso,                      // ISO
        BigDecimal focalLength,           // 초점거리
        String createdAt                  // 생성시각(스팟, KST, ISO-8601)
) {

    public static SpotDetailResponse from (Spot spot, User spotOwner, SpotPhoto photo, EAttractionLevel attractionLevel) {
        return SpotDetailResponse.builder()
                .spotId(spot.getId())
                .profileImage(spotOwner.getProfileImage())
                .nickname(spotOwner.getNickname())
                .alias(spot.getAlias())
                .fileUrl(photo.getFileUrl())
                .address(spot.getFullAddress())
                .attractionLevel(attractionLevel)
                .takenAt(toKstIso(photo.getTakenAt())) // LocalDateTime/Instant/OffsetDateTime 대응
                .latitude(photo.getLatitude())
                .longitude(photo.getLongitude())
                .cameraMake(photo.getCameraMake())
                .cameraModel(photo.getCameraModel())
                .aperture(photo.getAperture())
                .shutterSpeed(photo.getShutterSpeed())
                .iso(photo.getIso())
                .focalLength(photo.getFocalLength())
                .createdAt(toKstIso(spot.getCreatedAt()))
                .build();
    }

    // 시간 포맷터 (KST 기준)
    private static String toKstIso(Object temporal) {
        if (temporal == null) return null;
        LocalDateTime localDateTime;

        if (temporal instanceof Instant i) {
            localDateTime = LocalDateTime.ofInstant(i, ZoneId.of("Asia/Seoul"));
        } else if (temporal instanceof LocalDateTime ldt) {
            localDateTime = ldt;
        } else if (temporal instanceof OffsetDateTime odt) {
            localDateTime = odt.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        } else if (temporal instanceof ZonedDateTime zdt) {
            localDateTime = zdt.withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime();
        } else {
            throw new IllegalArgumentException("Unsupported temporal type: " + temporal.getClass());
        }

        localDateTime = localDateTime.truncatedTo(ChronoUnit.SECONDS);
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(localDateTime); // ex) 2025-10-27T21:34:56
    }
}
