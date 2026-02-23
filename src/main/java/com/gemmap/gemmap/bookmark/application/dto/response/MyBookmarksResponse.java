package com.gemmap.gemmap.bookmark.application.dto.response;

import com.gemmap.gemmap.spot.application.dto.response.SpotSummary;
import lombok.Builder;

import java.util.List;

@Builder
public record MyBookmarksResponse (
        List<SpotSummary> spots
) {
    public static MyBookmarksResponse of(List<SpotSummary> spots) {
        return MyBookmarksResponse.builder()
                .spots(spots)
                .build();
    }
}
