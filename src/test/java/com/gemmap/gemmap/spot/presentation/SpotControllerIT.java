package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.spot.domain.entity.Spot;
import com.gemmap.gemmap.spot.domain.entity.SpotPhoto;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 스팟 등록 API 통합 테스트
 * - MIME 검증 (415)
 * - 좌표 범위 검증 (400)
 * - 인증 검증 (401)
 * - 정상 등록 (201)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpotControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private SpotPhotoRepository spotPhotoRepository;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 각 테스트 전에 데이터 정리
        spotPhotoRepository.deleteAll();
        spotRepository.deleteAll();
    }

    /**
     * 테스트용 JWT Access Token 생성
     */
    private String getTestAccessToken() {
        return jwtUtil.generateAccessToken(TEST_USER_ID, ERole.USER);
    }

    @Test
    @DisplayName("정상: 유효한 JPEG 파일 업로드 시 201 Created 반환")
    void createSpot_withValidJpeg_returns201() throws Exception {
        // Given: JPEG 매직넘버 시그니처
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When: 스팟 등록 API 호출
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "테스트 스팟")
                .param("address", "서울특별시 강남구 테헤란로 123")
                .param("latitude", "37.5665")
                .param("longitude", "126.9780")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            // Then: 201 Created + spotId, fileUrl 반환
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.spotId").exists())
            .andExpect(jsonPath("$.data.fileUrl").exists())
            .andExpect(jsonPath("$.error").doesNotExist());

        // DB 검증
        List<Spot> spots = spotRepository.findAll();
        assertThat(spots).hasSize(1);
        assertThat(spots.get(0).getAlias()).isEqualTo("테스트 스팟");
        assertThat(spots.get(0).getUserId()).isEqualTo(TEST_USER_ID);

        List<SpotPhoto> photos = spotPhotoRepository.findAll();
        assertThat(photos).hasSize(1);
        assertThat(photos.get(0).getType()).isEqualTo(SpotPhoto.Type.SPOT);
        assertThat(photos.get(0).getFileUrl()).contains("spots/");
    }

    @Test
    @DisplayName("실패: 지원하지 않는 MIME 타입 (text/plain) → 415 Unsupported Media Type")
    void createSpot_withUnsupportedMime_returns415() throws Exception {
        // Given: 텍스트 파일
        MockMultipartFile badFile = new MockMultipartFile(
            "file", "doc.txt", "text/plain", "not-image-content".getBytes()
        );

        // When & Then: 415 Unsupported Media Type
        mockMvc.perform(multipart("/api/v1/spots")
                .file(badFile)
                .param("alias", "잘못된 파일")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.error.code").value(40003)) // UNSUPPORTED_MEDIA_TYPE
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("실패: MIME 스푸핑 (Content-Type: image/jpeg, 실제: text/plain) → 415")
    void createSpot_withMimeSpoofing_returns415() throws Exception {
        // Given: Content-Type은 image/jpeg이지만 실제 내용은 텍스트
        MockMultipartFile spoofedFile = new MockMultipartFile(
            "file", "fake.jpg", "image/jpeg", "This is not a JPEG file".getBytes()
        );

        // When & Then: 매직넘버 검증 실패 → 415
        mockMvc.perform(multipart("/api/v1/spots")
                .file(spoofedFile)
                .param("alias", "스푸핑 파일")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.error.code").value(40003));
    }

    @Test
    @DisplayName("정상: WebP 시그니처 정상 처리 → 201")
    void createSpot_withWebpSignature_returns201() throws Exception {
        // Given: WebP 매직넘버 (RIFF...WEBP)
        byte[] webpSignature = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        MockMultipartFile file = new MockMultipartFile(
            "file", "image.webp", "image/webp", webpSignature
        );

        // When & Then: 201 Created
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "WebP 테스트")
                .param("address", "서울특별시 종로구")
                .param("latitude", "37.5700")
                .param("longitude", "126.9800")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.spotId").exists())
            .andExpect(jsonPath("$.data.fileUrl").exists());
    }

    @Test
    @DisplayName("실패: 위도 범위 초과 (lat=999) → 400 Bad Request")
    void createSpot_withInvalidLatitude_returns400() throws Exception {
        // Given: 유효한 이미지 + 잘못된 위도
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When & Then: 400 Bad Request
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "잘못된 위도")
                .param("address", "서울특별시")
                .param("latitude", "999.0") // 유효 범위: -90 ~ 90
                .param("longitude", "127.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value(40000)) // INVALID_INPUT_VALUE
            .andExpect(jsonPath("$.error.message").value("위도는 -90~90 범위여야 합니다."));
    }

    @Test
    @DisplayName("실패: 경도 범위 초과 (lon=-200) → 400 Bad Request")
    void createSpot_withInvalidLongitude_returns400() throws Exception {
        // Given: 유효한 이미지 + 잘못된 경도
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When & Then: 400 Bad Request
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "잘못된 경도")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "-200.0") // 유효 범위: -180 ~ 180
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value(40000))
            .andExpect(jsonPath("$.error.message").value("경도는 -180~180 범위여야 합니다."));
    }

    @Test
    @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
    void createSpot_withoutAuthentication_returns401() throws Exception {
        // Given: 유효한 이미지
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When & Then: 401 Unauthorized
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "인증 없음")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "127.0"))
            // Authorization 헤더 없음
            .andDo(print())
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("실패: 잘못된 JWT 토큰 → 401 Unauthorized")
    void createSpot_withInvalidToken_returns401() throws Exception {
        // Given: 유효한 이미지 + 잘못된 토큰
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When & Then: 401 Unauthorized
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "잘못된 토큰")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-12345"))
            .andDo(print())
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정상: PNG 파일 업로드 → 201")
    void createSpot_withPngImage_returns201() throws Exception {
        // Given: PNG 매직넘버 (0x89 PNG...)
        byte[] pngSignature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.png", "image/png", pngSignature
        );

        // When & Then: 201 Created
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "PNG 테스트")
                .param("address", "부산광역시 해운대구")
                .param("latitude", "35.1595")
                .param("longitude", "129.1600")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.spotId").exists());
    }

    @Test
    @DisplayName("정상: EXIF 메타데이터 포함 업로드 → 201")
    void createSpot_withExifMetadata_returns201() throws Exception {
        // Given: 유효한 이미지 + EXIF 메타데이터
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When: EXIF 메타데이터 포함
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "EXIF 테스트")
                .param("address", "제주특별자치도 제주시")
                .param("latitude", "33.4996")
                .param("longitude", "126.5312")
                .param("takenAt", "2025-10-27T12:34:56Z") // UTC ISO8601
                .param("cameraMake", "Canon")
                .param("cameraModel", "EOS R5")
                .param("aperture", "2.8")
                .param("shutterSpeed", "1/125")
                .param("iso", "400")
                .param("focalLength", "50.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.spotId").exists());

        // DB 검증: EXIF 데이터 저장 확인
        List<SpotPhoto> photos = spotPhotoRepository.findAll();
        assertThat(photos).hasSize(1);
        SpotPhoto photo = photos.get(0);
        assertThat(photo.getCameraMake()).isEqualTo("Canon");
        assertThat(photo.getCameraModel()).isEqualTo("EOS R5");
        assertThat(photo.getAperture()).isEqualByComparingTo("2.8");
        assertThat(photo.getIso()).isEqualTo(400);
        assertThat(photo.getTakenAt()).isNotNull();
    }

    @Test
    @DisplayName("실패: 잘못된 UTC 날짜 형식 → 400")
    void createSpot_withInvalidDateFormat_returns400() throws Exception {
        // Given: 유효한 이미지 + 잘못된 날짜 형식
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", jpegSignature
        );

        // When & Then: 400 Bad Request
        mockMvc.perform(multipart("/api/v1/spots")
                .file(file)
                .param("alias", "잘못된 날짜")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .param("takenAt", "2025-10-27 12:34:56") // ISO8601 아님
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value(40000))
            .andExpect(jsonPath("$.error.message").value("takenAt은 UTC ISO8601 형식이어야 합니다."));
    }

    @Test
    @DisplayName("실패: 빈 파일 업로드 → 400")
    void createSpot_withEmptyFile_returns400() throws Exception {
        // Given: 빈 파일
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.jpg", "image/jpeg", new byte[0]
        );

        // When & Then: 400 Bad Request
        mockMvc.perform(multipart("/api/v1/spots")
                .file(emptyFile)
                .param("alias", "빈 파일")
                .param("address", "서울특별시")
                .param("latitude", "37.5")
                .param("longitude", "127.0")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value(40000))
            .andExpect(jsonPath("$.error.message").value("파일이 비어있습니다."));
    }
}
