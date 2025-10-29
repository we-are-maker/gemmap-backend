package com.gemmap.gemmap.spot.application.dto.response;

import com.gemmap.gemmap.spot.domain.entity.Spot;
import lombok.Builder;

/**
 * 스팟 요약 정보 DTO (내가 제보한 스팟 목록용)
 */
@Builder
public record SpotSummary(
        Long spotId,       // 스팟 ID
        String fileUrl,    // 사진 URL
        String alias,      // 스팟 별칭
        String address     // 도로명주소
) {
    public static SpotSummary of(Spot spot, String fileUrl) {
        return SpotSummary.builder()
                .spotId(spot.getId())
                .fileUrl(fileUrl)
                .alias(spot.getAlias())
                .address(spot.getAddress())
                .build();
    }
}
