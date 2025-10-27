package com.gemmap.gemmap.spot.application.dto.response;

import lombok.Builder;

@Builder
public record SpotCreateResponse(
    Long spotId,
    String fileUrl
) {}
