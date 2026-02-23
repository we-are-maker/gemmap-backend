package com.gemmap.gemmap.spot.application.dto.response;

import lombok.Builder;

import java.util.List;

@Builder
public record MyCreatedSpotsResponse(
        List<SpotSummary> spots
) {
    public static MyCreatedSpotsResponse of(List<SpotSummary> spots) {
        return MyCreatedSpotsResponse.builder()
                .spots(spots)
                .build();
    }
}
