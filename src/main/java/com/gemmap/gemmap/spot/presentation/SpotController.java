package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.dto.response.MySpotsResponse;
import com.gemmap.gemmap.spot.application.dto.response.SpotCreateResponse;
import com.gemmap.gemmap.spot.application.dto.response.SpotDetailResponse;
import com.gemmap.gemmap.spot.application.dto.response.SpotsResponse;
import com.gemmap.gemmap.spot.application.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/spots")
@RequiredArgsConstructor
public class SpotController {

    private final SpotService spotService;

    /**
     * 스팟 생성
     *
     * @param userId 인증된 사용자 ID
     * @param file 업로드할 이미지 파일
     * @param alias 스팟 별칭
     * @param address 도로명 주소
     * @param takenAt 촬영 시각 (UTC ISO8601 형식, optional)
     * @param latitude 위도
     * @param longitude 경도
     * @param cameraMake 카메라 브랜드 (optional)
     * @param cameraModel 카메라 모델 (optional)
     * @param aperture 조리개 값 (optional)
     * @param shutterSpeed 셔터 속도 (optional)
     * @param iso ISO 값 (optional)
     * @param focalLength 초점 거리 (optional)
     * @return 생성된 스팟 정보
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SpotCreateResponse> create(
            @UserId Long userId,
            @RequestParam MultipartFile file,
            @RequestParam String alias,
            // 주소 관련 파라미터
            @RequestParam String sido,
            @RequestParam String sigungu,
            @RequestParam(required = false) String eupmyeondong,
            @RequestParam(required = false) String roadName,
            @RequestParam(required = false) String buildingNo,
            @RequestParam String fullAddress,
            // 기타 파라미터
            @RequestParam(required = false) String takenAt,
            @RequestParam BigDecimal latitude,
            @RequestParam BigDecimal longitude,
            @RequestParam(required = false) String cameraMake,
            @RequestParam(required = false) String cameraModel,
            @RequestParam(required = false) BigDecimal aperture,
            @RequestParam(required = false) String shutterSpeed,
            @RequestParam(required = false) Integer iso,
            @RequestParam(required = false) BigDecimal focalLength
    ) {
        SpotCreateRequest request = new SpotCreateRequest(
            alias, sido, sigungu, eupmyeondong, roadName, buildingNo, fullAddress,
            takenAt, latitude, longitude, cameraMake, cameraModel, aperture, shutterSpeed, iso, focalLength
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(spotService.create(userId, request, file));
    }

    /**
     * 스팟 삭제
     *
     * @param userId 인증된 사용자 ID
     * @param spotId 삭제할 스팟 ID
     * @return 삭제 성공 응답
     */
    @DeleteMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> delete(
            @UserId Long userId,
            @RequestParam Long spotId
    ) {
        spotService.delete(userId, spotId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 스팟 상세 조회
     *
     * @param userId 인증된 사용자 ID
     * @param spotId 조회할 스팟 ID
     * @return 스팟 상세 정보
     */
    @GetMapping("/{spotId}")
    public ResponseEntity<SpotDetailResponse> getSpotDetail(
            @UserId Long userId,
            @PathVariable Long spotId
    ) {
        return ResponseEntity.ok(spotService.getSpotDetail(userId, spotId));
    }

    /**
     * 내가 제보한 스팟 목록 조회
     *
     * @param userId 인증된 사용자 ID
     * @return 내가 제보한 스팟 목록 (프로필 정보 + 스팟 목록)
     */
    @GetMapping("/me")
    public ResponseEntity<MySpotsResponse> getMySpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(spotService.getMySpots(userId));
    }

    /**
     * 지도 전체 마커 조회
     *
     * @param userId 인증된 사용자 ID
     */
    @GetMapping("/markers")
    public ResponseEntity<SpotsResponse> getSpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(spotService.getSpots(userId));
    }
}
