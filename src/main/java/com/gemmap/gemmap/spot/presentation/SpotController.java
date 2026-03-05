package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.bookmark.application.dto.response.MyBookmarksResponse;
import com.gemmap.gemmap.bookmark.application.service.BookmarkService;
import com.gemmap.gemmap.checkin.application.dto.response.MyCheckinSpotsResponse;
import com.gemmap.gemmap.checkin.application.service.CheckinService;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.dto.response.*;
import com.gemmap.gemmap.spot.application.service.SpotService;
import lombok.Getter;
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
    private final BookmarkService bookmarkService;
    private final CheckinService checkinService;

    /**
     * 스팟 생성
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
     * DELETE /api/v1/spots/{spotId}
     */
    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> delete(
            @UserId Long userId,
            @PathVariable Long spotId
    ) {
        spotService.delete(userId, spotId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 스팟 상세 조회
     */
    @GetMapping("/{spotId}")
    public ResponseEntity<SpotDetailResponse> getSpotDetail(
            @UserId Long userId,
            @PathVariable Long spotId
    ) {
        return ResponseEntity.ok(spotService.getSpotDetail(userId, spotId));
    }

    /**
     * 마이 스팟 조회 (사용자 정보 + 카운트: 제보/찜)
     */
    @GetMapping("/me")
    public ResponseEntity<MySpotsResponse> getMySpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(spotService.getMySpots(userId));
    }

    /**
     * 제보한 젬 목록 조회 (마이스팟)
     */
    @GetMapping("/me/created")
    public ResponseEntity<MyCreatedSpotsResponse> getMyCreatedSpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(spotService.getMyCreatedSpots(userId));
    }

    /**
     * 찜한 젬 목록 조회 (마이스팟)
     */
    @GetMapping("/me/bookmarked")
    public ResponseEntity<MyBookmarksResponse> getMyBookmarkedSpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(bookmarkService.getMyBookmarkedSpots(userId));
    }

    /**
     * 체크인 젬 목록 조회 (마이스팟)
     */
    @GetMapping("/me/checked")
    public ResponseEntity<MyCheckinSpotsResponse> getMyCheckinSpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(checkinService.getMyCheckinSpots(userId));
    }

    /**
     * 지도 전체 마커 조회
     */
    @GetMapping("/markers")
    public ResponseEntity<SpotsResponse> getSpots(
            @UserId Long userId
    ) {
        return ResponseEntity.ok(spotService.getSpots(userId));
    }
}
