package com.gemmap.gemmap.spot.application.dto.response;

import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/** 마커 조회 응답 DTO **/
public record MarkerResponse(
        List<MarkerInfo> markers
) {

    /** 개별 마커 정보 **/
    public record MarkerInfo (
        Long spotId,
        BigDecimal latitude,
        BigDecimal longitude
    ) {}

    /** SpotPhoto 엔티티 목록을 MarkerResponse로 변환 */
    public static MarkerResponse of(List<SpotPhoto> photos) {
        List<MarkerInfo> markers = photos.stream()
                .map(photo -> new MarkerInfo(
                        photo.getSpot().getId(),
                        photo.getLatitude(),
                        photo.getLongitude()))
                .toList();

        return new MarkerResponse(markers);
    }
}
