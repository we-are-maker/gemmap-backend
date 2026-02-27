package com.gemmap.gemmap.checkin.presentation;

import com.gemmap.gemmap.checkin.application.dto.request.CheckinCreateRequest;
import com.gemmap.gemmap.checkin.application.dto.response.CheckinCreateResponse;
import com.gemmap.gemmap.checkin.application.service.CheckinService;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.common.enums.ERecommendationLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/checkins")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /**
     * 젬 획득하기 (체크인)
     * POST /api/v1/checkins
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CheckinCreateResponse> createCheckin(
            @UserId Long userId,
            @RequestParam Long spotId,
            @RequestParam MultipartFile file,
            @RequestParam ERecommendationLevel recommendationLevel,
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
        CheckinCreateRequest request = new CheckinCreateRequest(
                spotId, recommendationLevel, takenAt, latitude, longitude,
                cameraMake, cameraModel, aperture, shutterSpeed, iso, focalLength
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checkinService.createCheckin(userId, request, file));
    }

    /**
     * 체크인 취소
     * DELETE /api/v1/checkins/{spotId}
     */
    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> deleteCheckin(
            @UserId Long userId,
            @PathVariable Long spotId
    ) {
        checkinService.deleteCheckin(userId, spotId);
        return ResponseEntity.noContent().build();
    }
}
