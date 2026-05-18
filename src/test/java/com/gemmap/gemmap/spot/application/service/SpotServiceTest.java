package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3PresignedUrlService;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.support.SpotFileValidator;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
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
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SpotService 단위 테스트
 * - 주소 필드 저장 검증
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpotPhotoRepository spotPhotoRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private S3PresignedUrlService s3PresignedUrlService;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private SpotFileValidator spotFileValidator;

    @InjectMocks
    private SpotService spotService;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_FILE_URL = "https://test.com/spots/2025/10/uuid.jpg";

    private User testUser;
    private MockMultipartFile testFile;

    @BeforeEach
    void setUp() {
        // S3Properties Mock 설정 (lenient: 호출되지 않을 수 있음)
        lenient().when(s3Properties.getBucket()).thenReturn(TEST_BUCKET);

        // 테스트용 User Mock
        testUser = mock(User.class);
        lenient().when(testUser.getId()).thenReturn(TEST_USER_ID);

        // 테스트용 JPEG 파일
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        testFile = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );
    }

    private SpotCreateRequest createRequest(String alias, String sido, String sigungu,
                                            String fullAddress, BigDecimal lat, BigDecimal lon) {
        return new SpotCreateRequest(
            alias, sido, sigungu, null, null, null, fullAddress,
            null, lat, lon, null, null, null, null, null, null
        );
    }

    private void mockUserFound() {
        given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(testUser));
        given(testUser.hasPhotoConsentAgreed()).willReturn(true);
    }

    private void mockS3UploadSuccess() throws Exception {
        doNothing().when(objectStorageService).upload(eq(TEST_BUCKET), anyString(), any());
        given(s3PresignedUrlService.generatePresignedGetUrl(anyString())).willReturn(TEST_FILE_URL);
    }

    // =====================================================================
    // 주소 필드 저장 검증
    // =====================================================================

    @Nested
    @DisplayName("주소 필드 저장 검증")
    class AddressFieldTests {

        @Test
        @DisplayName("스팟 생성 시 shortAddress 자동 생성 (sido + \" \" + sigungu)")
        void create_shouldGenerateShortAddress() throws Exception {
            // Given
            mockUserFound();
            doNothing().when(spotFileValidator).validateImage(testFile);
            mockS3UploadSuccess();

            SpotCreateRequest request = createRequest(
                "자연광맛집", "경기도", "남양주시",
                "대한민국 경기도 남양주시 와부읍 경강로926번길 20",
                BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0)
            );

            ArgumentCaptor<Spot> spotCaptor = ArgumentCaptor.forClass(Spot.class);
            Spot savedSpot = Spot.builder()
                .user(testUser)
                .sido("경기도").sigungu("남양주시")
                .fullAddress("대한민국 경기도 남양주시 와부읍 경강로926번길 20")
                .shortAddress("경기도 남양주시")
                .alias("자연광맛집")
                .build();
            given(spotRepository.save(spotCaptor.capture())).willReturn(savedSpot);
            given(spotPhotoRepository.save(any(SpotPhoto.class))).willReturn(mock(SpotPhoto.class));

            // When
            spotService.create(TEST_USER_ID, request, testFile);

            // Then: shortAddress가 "경기도 남양주시"로 자동 생성됨
            Spot capturedSpot = spotCaptor.getValue();
            assertThat(capturedSpot.getShortAddress()).isEqualTo("경기도 남양주시");
        }

        @Test
        @DisplayName("스팟 생성 시 주소 필드 전체 저장 검증")
        void create_shouldSaveAllAddressFields() throws Exception {
            // Given
            mockUserFound();
            doNothing().when(spotFileValidator).validateImage(testFile);
            mockS3UploadSuccess();

            SpotCreateRequest request = new SpotCreateRequest(
                "테스트스팟", "서울특별시", "강남구", "삼성동", "테헤란로", "123",
                "대한민국 서울특별시 강남구 삼성동 테헤란로 123",
                null, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
                null, null, null, null, null, null
            );

            ArgumentCaptor<Spot> spotCaptor = ArgumentCaptor.forClass(Spot.class);
            Spot savedSpot = Spot.builder()
                .user(testUser)
                .sido("서울특별시").sigungu("강남구").eupmyeondong("삼성동")
                .roadName("테헤란로").buildingNo("123")
                .fullAddress("대한민국 서울특별시 강남구 삼성동 테헤란로 123")
                .shortAddress("서울특별시 강남구")
                .alias("테스트스팟")
                .build();
            given(spotRepository.save(spotCaptor.capture())).willReturn(savedSpot);
            given(spotPhotoRepository.save(any(SpotPhoto.class))).willReturn(mock(SpotPhoto.class));

            // When
            spotService.create(TEST_USER_ID, request, testFile);

            // Then: 모든 주소 필드가 정확히 저장됨
            Spot capturedSpot = spotCaptor.getValue();
            assertThat(capturedSpot.getSido()).isEqualTo("서울특별시");
            assertThat(capturedSpot.getSigungu()).isEqualTo("강남구");
            assertThat(capturedSpot.getEupmyeondong()).isEqualTo("삼성동");
            assertThat(capturedSpot.getRoadName()).isEqualTo("테헤란로");
            assertThat(capturedSpot.getBuildingNo()).isEqualTo("123");
            assertThat(capturedSpot.getFullAddress()).isEqualTo("대한민국 서울특별시 강남구 삼성동 테헤란로 123");
            assertThat(capturedSpot.getShortAddress()).isEqualTo("서울특별시 강남구");
        }
    }

    // =====================================================================
    // 좌표 검증 테스트
    // =====================================================================

    @Nested
    @DisplayName("좌표 범위 검증")
    class CoordinateValidationTests {

        @Test
        @DisplayName("위도 범위 초과 → INVALID_INPUT_VALUE 예외")
        void create_whenInvalidLatitude_thenThrowInvalidInputValue() {
            // Given
            mockUserFound();

            SpotCreateRequest request = createRequest(
                "테스트 스팟", "서울특별시", "강남구",
                "서울특별시 강남구", BigDecimal.valueOf(999.0), BigDecimal.valueOf(127.0)
            );

            // When & Then
            assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, testFile))
                .isInstanceOf(CommonException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("위도는 -90~90 범위여야 합니다");

            verify(spotFileValidator, never()).validateImage(any());
            verify(objectStorageService, never()).upload(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("경도 범위 초과 → INVALID_INPUT_VALUE 예외")
        void create_whenInvalidLongitude_thenThrowInvalidInputValue() {
            // Given
            mockUserFound();

            SpotCreateRequest request = createRequest(
                "테스트 스팟", "서울특별시", "강남구",
                "서울특별시 강남구", BigDecimal.valueOf(37.5), BigDecimal.valueOf(-200.0)
            );

            // When & Then
            assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, testFile))
                .isInstanceOf(CommonException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
                .hasMessageContaining("경도는 -180~180 범위여야 합니다");

            verify(spotFileValidator, never()).validateImage(any());
            verify(objectStorageService, never()).upload(anyString(), anyString(), any());
        }
    }
}
