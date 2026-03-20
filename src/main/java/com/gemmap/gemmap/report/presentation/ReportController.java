package com.gemmap.gemmap.report.presentation;

import com.gemmap.gemmap.report.application.dto.request.SpotReportRequest;
import com.gemmap.gemmap.report.application.dto.response.SpotReportResponse;
import com.gemmap.gemmap.report.application.service.ReportService;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spots/{spotId}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 스팟 신고
     * POST /api/v1/spots/{spotId}/reports
     */
    @PostMapping
    public ResponseEntity<SpotReportResponse> createReport(
            @UserId Long userId,
            @PathVariable Long spotId,
            @Valid @RequestBody SpotReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.createReport(userId, spotId, request));
    }
}
