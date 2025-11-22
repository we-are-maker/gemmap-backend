package com.gemmap.gemmap.spot.application.dto.response;

import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 마커 정보 DTO (지도 전체 마커 조회용)
 */
@Builder
public record SpotMakerInfo(
        Long spotId,            // 스팟 ID
        BigDecimal latitude,    // 위도
        BigDecimal longitude    // 경도
) {}
