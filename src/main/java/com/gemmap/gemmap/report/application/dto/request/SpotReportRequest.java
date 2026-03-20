package com.gemmap.gemmap.report.application.dto.request;

import com.gemmap.gemmap.shared.common.enums.EReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SpotReportRequest(
        @NotNull EReportReason reason,
        @Size(max = 100) String content
) {
}
