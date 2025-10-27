package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.common.dto.ResponseDto;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.dto.response.SpotCreateResponse;
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
}
