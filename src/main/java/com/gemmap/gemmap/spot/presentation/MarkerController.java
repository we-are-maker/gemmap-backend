package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.spot.application.dto.request.MarkerQueryRequest;
import com.gemmap.gemmap.spot.application.dto.response.MarkerResponse;
import com.gemmap.gemmap.spot.application.service.MarkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/markers")
@RequiredArgsConstructor
public class MarkerController {

    private final MarkerService markerService;

    /**
     * Bounding Box 내 마커 조회 (LOD 적용)
     *
     * @param userId 인증된 사용자 ID (@UserId 어노테이션으로 JWT에서 추출)
     * @param request Bounding Box 좌표 및 줌 레벨 (Bean Validation으로 범위 검증)
     * @return 마커 목록 및 반환 개수
     */
    @GetMapping
    public ResponseEntity<MarkerResponse> getMarkers(
            @UserId Long userId,
            @Valid MarkerQueryRequest request) {
        return ResponseEntity.ok(markerService.getMarkersWithLOD(request));
    }
}
