package com.gemmap.gemmap.checkin.application.dto.response;

import com.gemmap.gemmap.checkin.domain.entity.SpotCheckin;
import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;
import lombok.Builder;

@Builder
public record CheckinCreateResponse(
        Long checkinId,
        Long spotId,
        String fileUrl,
        String alias,
        String address,
        ERecommendationLevel recommendationLevel
) {
    public static CheckinCreateResponse of(SpotCheckin checkin, String fileUrl) {
        return CheckinCreateResponse.builder()
                .checkinId(checkin.getId())
                .spotId(checkin.getSpot().getId())
                .fileUrl(fileUrl)
                .alias(checkin.getSpot().getAlias())
                .address(checkin.getSpot().getShortAddress())
                .recommendationLevel(checkin.getRecommendationLevel())
                .build();
    }
}
