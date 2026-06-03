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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final Long TEST_SPOT_ID = 10L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotReportRepository spotReportRepository;

    @InjectMocks
    private ReportService reportService;

    private User user;
    private User owner;
    private Spot spot;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        owner = mock(User.class);
        spot = mock(Spot.class);

        lenient().when(user.getId()).thenReturn(TEST_USER_ID);
        lenient().when(owner.getId()).thenReturn(OTHER_USER_ID);
        lenient().when(spot.getId()).thenReturn(TEST_SPOT_ID);
        lenient().when(spot.getUser()).thenReturn(owner);
    }

    private void mockUserAndSpotFound() {
        given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
        given(spotRepository.findById(TEST_SPOT_ID)).willReturn(Optional.of(spot));
    }

    @Nested
    @DisplayName("신고 생성")
    class CreateReport {

        @Test
        @DisplayName("OTHER 사유는 content를 trim 후 저장한다")
        void createReport_trimsOtherContent() {
            mockUserAndSpotFound();
            given(spotReportRepository.existsByUserAndSpot(user, spot)).willReturn(false);

            ArgumentCaptor<SpotReport> reportCaptor = ArgumentCaptor.forClass(SpotReport.class);
            SpotReport savedReport = SpotReport.builder()
                    .user(user)
                    .spot(spot)
                    .reason(EReportReason.OTHER)
                    .content("신고 내용")
                    .build();
            given(spotReportRepository.save(reportCaptor.capture())).willReturn(savedReport);

            SpotReportResponse response = reportService.createReport(
                    TEST_USER_ID,
                    TEST_SPOT_ID,
                    new SpotReportRequest(EReportReason.OTHER, "  신고 내용  ")
            );

            assertThat(reportCaptor.getValue().getContent()).isEqualTo("신고 내용");
            assertThat(response.reportId()).isNull();
        }

        @Test
        @DisplayName("OTHER가 아닌 사유는 content를 null로 정리한다")
        void createReport_nonOtherReasonDropsContent() {
            mockUserAndSpotFound();
            given(spotReportRepository.existsByUserAndSpot(user, spot)).willReturn(false);

            ArgumentCaptor<SpotReport> reportCaptor = ArgumentCaptor.forClass(SpotReport.class);
            given(spotReportRepository.save(reportCaptor.capture()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            reportService.createReport(
                    TEST_USER_ID,
                    TEST_SPOT_ID,
                    new SpotReportRequest(EReportReason.LOCATION_MISMATCH, "  무시될 내용  ")
            );

            assertThat(reportCaptor.getValue().getContent()).isNull();
        }

        @Test
        @DisplayName("본인 스팟 신고는 CANNOT_REPORT_OWN_SPOT 예외")
        void createReport_ownSpotThrowsForbidden() {
            given(owner.getId()).willReturn(TEST_USER_ID);
            mockUserAndSpotFound();

            assertThatThrownBy(() -> reportService.createReport(
                    TEST_USER_ID,
                    TEST_SPOT_ID,
                    new SpotReportRequest(EReportReason.LOCATION_MISMATCH, null)
            ))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_REPORT_OWN_SPOT);
        }

        @Test
        @DisplayName("OTHER 사유에 content가 없으면 null로 저장한다")
        void createReport_blankOtherContentSavesNull() {
            mockUserAndSpotFound();
            given(spotReportRepository.existsByUserAndSpot(user, spot)).willReturn(false);

            ArgumentCaptor<SpotReport> reportCaptor = ArgumentCaptor.forClass(SpotReport.class);
            given(spotReportRepository.save(reportCaptor.capture()))
                    .willAnswer(invocation -> invocation.getArgument(0));

            reportService.createReport(
                    TEST_USER_ID,
                    TEST_SPOT_ID,
                    new SpotReportRequest(EReportReason.OTHER, "   ")
            );

            assertThat(reportCaptor.getValue().getContent()).isNull();
        }

        @Test
        @DisplayName("중복 신고는 REPORT_ALREADY_EXISTS 예외")
        void createReport_duplicateThrowsConflict() {
            mockUserAndSpotFound();
            given(spotReportRepository.existsByUserAndSpot(user, spot)).willReturn(true);

            assertThatThrownBy(() -> reportService.createReport(
                    TEST_USER_ID,
                    TEST_SPOT_ID,
                    new SpotReportRequest(EReportReason.LOCATION_MISMATCH, null)
            ))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("DB 유니크 제약 충돌도 REPORT_ALREADY_EXISTS로 변환한다")
        void createReport_dataIntegrityViolationThrowsConflict() {
            mockUserAndSpotFound();
            given(spotReportRepository.existsByUserAndSpot(user, spot)).willReturn(false);
            given(spotReportRepository.save(any(SpotReport.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> reportService.createReport(
                    TEST_USER_ID,
                    TEST_SPOT_ID,
                    new SpotReportRequest(EReportReason.LOCATION_MISMATCH, null)
            ))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_ALREADY_EXISTS);
        }
    }
}
