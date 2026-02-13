package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.shared.common.enums.ESpotPhotoType;
import com.gemmap.gemmap.spot.application.dto.request.MarkerQueryRequest;
import com.gemmap.gemmap.spot.application.dto.response.MarkerResponse;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarkerService {

    private final SpotPhotoRepository spotPhotoRepository;

    /** 줌 레벨에 따른 LOD를 적용하여 마커 조회 **/
    @Transactional(readOnly = true)
    public MarkerResponse getMarkersWithLOD(MarkerQueryRequest request) {
        int limit = calculateLimitByZoom(request.zoom());

        List<SpotPhoto> photos = spotPhotoRepository.findWithinBoundingBox(
                request.swLng(), request.swLat(),
                request.neLng(), request.neLat(),
                ESpotPhotoType.SPOT.name(), limit
        );

        return MarkerResponse.of(photos);
    }

    /** 줌 레벨에 따른 최대 반환 개수 계산 (LOD)
     *
     * 줌 레벨이 낮을수록 적은 마커, 높을수록 많은 마커를 반환한다.
     */
    private int calculateLimitByZoom(int zoom) {
        if (zoom <= 8) return 100;
        else if (zoom <= 10) return 300;
        else if (zoom <= 12) return 500;
        else if (zoom <= 14) return 1000;
        else if (zoom <= 16) return 2000;
        else return 5000;
    }
}
