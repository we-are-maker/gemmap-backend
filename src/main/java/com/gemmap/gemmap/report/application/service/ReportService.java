package com.gemmap.gemmap.report.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.report.application.dto.request.SpotReportRequest;
import com.gemmap.gemmap.report.application.dto.response.SpotReportResponse;
import com.gemmap.gemmap.report.domain.entity.SpotReport;
import com.gemmap.gemmap.report.domain.repository.SpotReportRepository;
import com.gemmap.gemmap.shared.common.enums.EReportReason;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final UserRepository userRepository;
    private final SpotRepository spotRepository;
    private final SpotReportRepository spotReportRepository;

    /**
     * 스팟 신고 접수
     */
    @Transactional
    public SpotReportResponse createReport(Long userId, Long spotId, SpotReportRequest request) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 스팟 조회
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CommonException(ErrorCode.SPOT_NOT_FOUND));

        // 본인 스팟 신고 방지
        if (spot.getUser().getId().equals(userId)) {
            throw new CommonException(ErrorCode.CANNOT_REPORT_OWN_SPOT);
        }

        // 중복 신고 확인
        if (spotReportRepository.existsByUserAndSpot(user, spot)) {
            throw new CommonException(ErrorCode.REPORT_ALREADY_EXISTS);
        }

        // 저장 (DataIntegrityViolationException: 동시 요청으로 인한 유니크 제약 위반 방어)
        SpotReport report = SpotReport.builder()
                .user(user)
                .spot(spot)
                .reason(request.reason())
                .content(validateAndNormalizeContent(request.reason(), request.content()))
                .build();

        try {
            return SpotReportResponse.of(spotReportRepository.save(report));
        } catch (DataIntegrityViolationException ex) {
            throw new CommonException(ErrorCode.REPORT_ALREADY_EXISTS);
        }
    }

    /**
     * 신고 사유에 맞는 content 필수 여부와 저장값 형식을 함께 정리한다.
     */
    private String validateAndNormalizeContent(EReportReason reason, String content) {
        // OTHER가 아닌 사유
        if (reason != EReportReason.OTHER) {
            return null; // content를 항상 null로 반환
        }

        // OTHER 사유 + content 없음(null / 빈 문자열 / 공백만)
        if (!StringUtils.hasText(content)) {
            throw new CommonException(ErrorCode.REPORT_CONTENT_REQUIRED);
        }

        // OTHER 사유 + content 있음
        return content.trim();
    }
}
