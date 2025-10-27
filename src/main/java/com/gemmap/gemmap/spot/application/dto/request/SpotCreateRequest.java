package com.gemmap.gemmap.spot.application.dto.request;

import java.math.BigDecimal;

public record SpotCreateRequest(
    String alias,
    String address,
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
