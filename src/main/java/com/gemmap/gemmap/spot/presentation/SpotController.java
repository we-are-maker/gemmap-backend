package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.common.dto.ResponseDto;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.dto.response.SpotCreateResponse;
import com.gemmap.gemmap.spot.application.dto.response.SpotDetailResponse;
import com.gemmap.gemmap.spot.application.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/spots")
@RequiredArgsConstructor
public class SpotController {

    private final SpotService spotService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<SpotCreateResponse> create(
            @UserId Long userId,
            @RequestParam(value = "file") MultipartFile file,
            @RequestParam(value = "alias") String alias,
            @RequestParam(value = "address") String address,
            @RequestParam(value = "takenAt", required = false) String takenAt,
            @RequestParam(value = "latitude") BigDecimal latitude,
            @RequestParam(value = "longitude") BigDecimal longitude,
            @RequestParam(value = "cameraMake", required = false) String cameraMake,
            @RequestParam(value = "cameraModel", required = false) String cameraModel,
            @RequestParam(value = "aperture", required = false) BigDecimal aperture,
            @RequestParam(value = "shutterSpeed", required = false) String shutterSpeed,
            @RequestParam(value = "iso", required = false) Integer iso,
            @RequestParam(value = "focalLength", required = false) BigDecimal focalLength
    ) {
        SpotCreateRequest request = new SpotCreateRequest(
            alias, address, takenAt, latitude, longitude,
            cameraMake, cameraModel, aperture, shutterSpeed, iso, focalLength
        );
        return ResponseDto.created(spotService.create(userId, request, file));
    }

    @DeleteMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<?> delete(
            @UserId Long userId,
            @RequestParam(value = "spotId") Long spotId
    ) {
        spotService.delete(userId, spotId);
        return ResponseDto.noContent();
    }

    /**
     * 스팟 상세 조회
     *
     * @param userId 인증된 사용자 ID
     * @param spotId 조회할 스팟 ID
     * @return 스팟 상세 정보
     */
    @GetMapping("/{spotId}")
    public ResponseDto<SpotDetailResponse> getSpotDetail(
            @UserId Long userId,
            @PathVariable Long spotId
    ) {
        return ResponseDto.ok(spotService.getSpotDetail(userId, spotId));
    }
}
