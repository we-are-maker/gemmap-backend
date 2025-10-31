package com.gemmap.gemmap.spot.application.dto.response;

import lombok.Builder;

import java.util.List;

/**
 * 지도 전체 마커 조회 응답 DTO
 */
@Builder
public record SpotsResponse(
        List<SpotMakerInfo> spots // 제보한 젬 목록
) {
    public static SpotsResponse of(List<SpotMakerInfo> spots) {
        return SpotsResponse.builder()
                .spots(spots)
                .build();
    }
}
