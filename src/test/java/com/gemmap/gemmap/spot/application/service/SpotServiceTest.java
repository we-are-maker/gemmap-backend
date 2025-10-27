package com.gemmap.gemmap.spot.application.service;

import com.gemmap.gemmap.image.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.image.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.spot.application.dto.request.SpotCreateRequest;
import com.gemmap.gemmap.spot.application.support.SpotFileValidator;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SpotService 단위 테스트
 * - 보상 트랜잭션 (DB 실패 시 S3 삭제)
 */
@ExtendWith(MockitoExtension.class)
class SpotServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotPhotoRepository spotPhotoRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private S3UrlGenerator s3UrlGenerator;

    @Mock
    private S3Properties s3Properties;

    @Mock
    private SpotFileValidator spotFileValidator;

    @InjectMocks
    private SpotService spotService;

    private static final Long TEST_USER_ID = 1L;
    private static final String TEST_BUCKET = "test-bucket";
    private static final String TEST_FILE_URL = "https://test.com/spots/2025/10/uuid.jpg";

    @BeforeEach
    void setUp() {
        // S3Properties Mock 설정 (lenient: 호출되지 않을 수 있음)
        lenient().when(s3Properties.getBucket()).thenReturn(TEST_BUCKET);
    }

    @Test
    @DisplayName("보상 트랜잭션: DB 저장 실패 시 S3 객체 삭제 호출")
    void create_whenDatabaseFailure_thenDeleteS3Object() throws Exception {
        // Given: 유효한 파일 + DB 저장 실패 시나리오
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        SpotCreateRequest request = new SpotCreateRequest(
            "테스트 스팟",
            "서울특별시",
            null,
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(127.0),
            null, null, null, null, null, null
        );

        // SpotFileValidator는 통과
        doNothing().when(spotFileValidator).validateImage(file);

        // S3 업로드 성공
        doNothing().when(objectStorageService).upload(eq(TEST_BUCKET), anyString(), eq(file));
        given(s3UrlGenerator.generateUrl(anyString())).willReturn(TEST_FILE_URL);

        // SpotRepository.save() 실패 (RuntimeException)
        given(spotRepository.save(any(Spot.class)))
            .willThrow(new RuntimeException("Database connection failed"));

        // When & Then: DB 실패로 예외 발생
        assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, file))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database connection failed");

        // Verify: S3 delete() 호출됨 (보상 트랜잭션)
        verify(objectStorageService, times(1))
            .delete(eq(TEST_BUCKET), anyString());
    }

    @Test
    @DisplayName("보상 트랜잭션: SpotPhoto 저장 실패 시 S3 객체 삭제 호출")
    void create_whenSpotPhotoSaveFailure_thenDeleteS3Object() throws Exception {
        // Given: Spot은 성공, SpotPhoto 저장 실패
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        SpotCreateRequest request = new SpotCreateRequest(
            "테스트 스팟",
            "서울특별시",
            null,
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(127.0),
            null, null, null, null, null, null
        );

        // 검증 통과
        doNothing().when(spotFileValidator).validateImage(file);

        // S3 업로드 성공
        doNothing().when(objectStorageService).upload(eq(TEST_BUCKET), anyString(), eq(file));
        given(s3UrlGenerator.generateUrl(anyString())).willReturn(TEST_FILE_URL);

        // Spot 저장 성공
        Spot savedSpot = Spot.builder()
            .userId(TEST_USER_ID)
            .alias("테스트 스팟")
            .address("서울특별시")
            .build();
        given(spotRepository.save(any(Spot.class))).willReturn(savedSpot);

        // SpotPhoto 저장 실패
        given(spotPhotoRepository.save(any()))
            .willThrow(new RuntimeException("Photo save failed"));

        // When & Then: SpotPhoto 저장 실패로 예외 발생
        assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, file))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Photo save failed");

        // Verify: S3 delete() 호출됨
        verify(objectStorageService, times(1))
            .delete(eq(TEST_BUCKET), anyString());
    }

    @Test
    @DisplayName("실패: S3 업로드 실패 시 FILE_UPLOAD_FAILED 예외")
    void create_whenS3UploadFailure_thenThrowFileUploadFailed() throws Exception {
        // Given: S3 업로드 실패
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        SpotCreateRequest request = new SpotCreateRequest(
            "테스트 스팟",
            "서울특별시",
            null,
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(127.0),
            null, null, null, null, null, null
        );

        // 검증 통과
        doNothing().when(spotFileValidator).validateImage(file);

        // S3 업로드 실패
        doThrow(new RuntimeException("S3 connection timeout"))
            .when(objectStorageService).upload(eq(TEST_BUCKET), anyString(), eq(file));

        // When & Then: FILE_UPLOAD_FAILED 예외 발생
        assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, file))
            .isInstanceOf(CommonException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_UPLOAD_FAILED);

        // Verify: DB 저장 시도 안 함
        verify(spotRepository, never()).save(any(Spot.class));

        // Verify: 보상 트랜잭션도 호출 안 됨 (업로드 자체가 실패했으므로)
        verify(objectStorageService, never()).delete(anyString(), anyString());
    }

    @Test
    @DisplayName("실패: 위도 범위 초과 → INVALID_INPUT_VALUE 예외")
    void create_whenInvalidLatitude_thenThrowInvalidInputValue() throws Exception {
        // Given: 잘못된 위도
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        SpotCreateRequest request = new SpotCreateRequest(
            "테스트 스팟",
            "서울특별시",
            null,
            BigDecimal.valueOf(999.0), // 유효 범위 초과
            BigDecimal.valueOf(127.0),
            null, null, null, null, null, null
        );

        // When & Then: INVALID_INPUT_VALUE 예외 (위도 범위 검증 실패)
        assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, file))
            .isInstanceOf(CommonException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
            .hasMessageContaining("위도는 -90~90 범위여야 합니다");

        // Verify: 좌표 검증이 먼저 실패하므로 파일 검증, S3 업로드 시도 안 함
        verify(spotFileValidator, never()).validateImage(any());
        verify(objectStorageService, never()).upload(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("실패: 경도 범위 초과 → INVALID_INPUT_VALUE 예외")
    void create_whenInvalidLongitude_thenThrowInvalidInputValue() throws Exception {
        // Given: 잘못된 경도
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        SpotCreateRequest request = new SpotCreateRequest(
            "테스트 스팟",
            "서울특별시",
            null,
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(-200.0), // 유효 범위 초과
            null, null, null, null, null, null
        );

        // When & Then: INVALID_INPUT_VALUE 예외 (경도 범위 검증 실패)
        assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, file))
            .isInstanceOf(CommonException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE)
            .hasMessageContaining("경도는 -180~180 범위여야 합니다");

        // Verify: 좌표 검증이 먼저 실패하므로 파일 검증, S3 업로드 시도 안 함
        verify(spotFileValidator, never()).validateImage(any());
        verify(objectStorageService, never()).upload(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("보상 트랜잭션: S3 삭제 실패해도 원래 예외 전파")
    void create_whenDbFailureAndS3DeleteFailure_thenPropagateOriginalException() throws Exception {
        // Given: DB 실패 + S3 삭제도 실패
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        SpotCreateRequest request = new SpotCreateRequest(
            "테스트 스팟",
            "서울특별시",
            null,
            BigDecimal.valueOf(37.5),
            BigDecimal.valueOf(127.0),
            null, null, null, null, null, null
        );

        // 검증 통과
        doNothing().when(spotFileValidator).validateImage(file);

        // S3 업로드 성공
        doNothing().when(objectStorageService).upload(eq(TEST_BUCKET), anyString(), eq(file));
        given(s3UrlGenerator.generateUrl(anyString())).willReturn(TEST_FILE_URL);

        // DB 저장 실패
        RuntimeException originalException = new RuntimeException("Database connection failed");
        given(spotRepository.save(any(Spot.class))).willThrow(originalException);

        // S3 삭제도 실패 (보상 트랜잭션 실패)
        doThrow(new RuntimeException("S3 delete failed"))
            .when(objectStorageService).delete(anyString(), anyString());

        // When & Then: 원래 예외(DB 실패)가 전파되어야 함
        assertThatThrownBy(() -> spotService.create(TEST_USER_ID, request, file))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Database connection failed"); // S3 삭제 실패가 아닌 원래 예외

        // Verify: S3 삭제 시도는 함
        verify(objectStorageService, times(1)).delete(anyString(), anyString());
    }
}
