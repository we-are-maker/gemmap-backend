package com.gemmap.gemmap.checkin.application.dto.request;

import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;

import java.math.BigDecimal;

public record CheckinCreateRequest(
        Long spotId,
        ERecommendationLevel recommendationLevel,
        String takenAt,
        BigDecimal latitude,
        BigDecimal longitude,
        String cameraMake,
        String cameraModel,
        BigDecimal aperture,
        String shutterSpeed,
        Integer iso,
        BigDecimal focalLength
) {}
