package com.gemmap.gemmap.spot.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 마커 조회 요청 DTO
 *
 * Bounding Box: SW(남서, 좌하단) ~ NE(북동, 우상단) 좌표와 줌 레벨을 전달받는다.
 */
public record MarkerQueryRequest(
        @NotNull @Min(-180) @Max(180) Double swLng, // 남서쪽 경도 (Bounding Box 좌하단)
        @NotNull @Min(-90) @Max(90) Double swLat, // 남서쪽 위도 (Bounding Box 좌하단)
        @NotNull @Min(-180) @Max(180) Double neLng, // 북동쪽 경도 (Bounding Box 우상단)
        @NotNull @Min(-90) @Max(90) Double neLat, // 북동쪽 위도 (Bounding Box 우상단)
        @NotNull @Min(1) @Max(21) Integer zoom // 현재 지도 줌 레벨
) {}
