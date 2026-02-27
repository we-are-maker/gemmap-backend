package com.gemmap.gemmap.checkin.application.dto.response;

import com.gemmap.gemmap.spot.application.dto.response.SpotSummary;
import lombok.Builder;

import java.util.List;

@Builder
public record MyCheckinSpotsResponse(
        List<SpotSummary> spots
) {
    public static MyCheckinSpotsResponse of(List<SpotSummary> spots) {
        return MyCheckinSpotsResponse.builder()
                .spots(spots)
                .build();
    }
}

