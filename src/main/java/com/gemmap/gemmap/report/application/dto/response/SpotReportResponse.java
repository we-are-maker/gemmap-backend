package com.gemmap.gemmap.report.application.dto.response;

import com.gemmap.gemmap.report.domain.entity.SpotReport;
import lombok.Builder;

@Builder
public record SpotReportResponse(
        Long reportId
) {

    public static SpotReportResponse of(SpotReport report) {
        return SpotReportResponse.builder()
                .reportId(report.getId())
                .build();
    }
}
