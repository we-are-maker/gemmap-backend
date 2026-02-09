package com.gemmap.gemmap.spot.presentation;

import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.spot.domain.repository.SpotPhotoRepository;
import com.gemmap.gemmap.spot.domain.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 스팟 등록 API 통합 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @Autowired
    private UserRepository userRepository;

    private Long testUserId;

    @BeforeEach
    void setUp() {
        spotPhotoRepository.deleteAll();
        spotRepository.deleteAll();

        // 테스트용 유저 생성 (isLogin=true, refreshToken 필수)
        User testUser = User.builder()
            .socialId("test-social-id")
            .eProvider(EProvider.KAKAO)
            .role(ERole.USER)
            .email("test@gemmap.com")
            .name("테스터")
            .nickname("test-user")
            .profileImage(null)
            .gender(null)
            .ageRange(null)
            .birthday(null)
            .birthyear(null)
            .build();
        testUser.updateLoginStatus(true);
        testUser.updateRefreshToken("test-refresh-token");
        testUser = userRepository.save(testUser);
        testUserId = testUser.getId();
    }

    private String getTestAccessToken() {
        return jwtUtil.generateAccessToken(testUserId, ERole.USER);
    }

    private MockMultipartFile jpegFile() {
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegSignature);
    }

    // =====================================================================
    // MIME 검증
    // =====================================================================

    @Nested
    @DisplayName("MIME 검증")
    class MimeValidationTests {

        @Test
        @DisplayName("지원하지 않는 MIME 타입 (text/plain) → error 응답")
        void createSpot_withUnsupportedMime_returnsError() throws Exception {
            MockMultipartFile badFile = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "not-image-content".getBytes()
            );

            mockMvc.perform(multipart("/api/v1/spots")
                    .file(badFile)
                    .param("alias", "잘못된 파일")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "37.5")
                    .param("longitude", "127.0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value(40003))
                .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("MIME 스푸핑 (Content-Type: image/jpeg, 실제: text/plain) → error 응답")
        void createSpot_withMimeSpoofing_returnsError() throws Exception {
            MockMultipartFile spoofedFile = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "This is not a JPEG file".getBytes()
            );

            mockMvc.perform(multipart("/api/v1/spots")
                    .file(spoofedFile)
                    .param("alias", "스푸핑 파일")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "37.5")
                    .param("longitude", "127.0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value(40003));
        }

        @Test
        @DisplayName("빈 파일 업로드 → error 응답")
        void createSpot_withEmptyFile_returnsError() throws Exception {
            MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
            );

            mockMvc.perform(multipart("/api/v1/spots")
                    .file(emptyFile)
                    .param("alias", "빈 파일")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "37.5")
                    .param("longitude", "127.0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value(40000));
        }
    }

    // =====================================================================
    // 좌표 범위 검증
    // =====================================================================

    @Nested
    @DisplayName("좌표 범위 검증")
    class CoordinateValidationTests {

        @Test
        @DisplayName("위도 범위 초과 (lat=999) → 400 error 응답")
        void createSpot_withInvalidLatitude_returnsError() throws Exception {
            mockMvc.perform(multipart("/api/v1/spots")
                    .file(jpegFile())
                    .param("alias", "잘못된 위도")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "999.0")
                    .param("longitude", "127.0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value(40000))
                .andExpect(jsonPath("$.error.message").value("위도는 -90~90 범위여야 합니다."));
        }

        @Test
        @DisplayName("경도 범위 초과 (lon=-200) → 400 error 응답")
        void createSpot_withInvalidLongitude_returnsError() throws Exception {
            mockMvc.perform(multipart("/api/v1/spots")
                    .file(jpegFile())
                    .param("alias", "잘못된 경도")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "37.5")
                    .param("longitude", "-200.0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + getTestAccessToken()))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.error.code").value(40000))
                .andExpect(jsonPath("$.error.message").value("경도는 -180~180 범위여야 합니다."));
        }
    }

    // =====================================================================
    // 인증 검증 (401 - Spring Security 처리)
    // =====================================================================

    @Nested
    @DisplayName("인증 검증")
    class AuthenticationTests {

        @Test
        @DisplayName("인증 토큰 없음 → 401")
        void createSpot_withoutAuthentication_returns401() throws Exception {
            mockMvc.perform(multipart("/api/v1/spots")
                    .file(jpegFile())
                    .param("alias", "인증 없음")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "37.5")
                    .param("longitude", "127.0"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 JWT 토큰 → 401")
        void createSpot_withInvalidToken_returns401() throws Exception {
            mockMvc.perform(multipart("/api/v1/spots")
                    .file(jpegFile())
                    .param("alias", "잘못된 토큰")
                    .param("sido", "서울특별시")
                    .param("sigungu", "강남구")
                    .param("fullAddress", "서울특별시 강남구")
                    .param("latitude", "37.5")
                    .param("longitude", "127.0")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-12345"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
        }
    }

}
