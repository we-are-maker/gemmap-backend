package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.application.dto.kakao.KakaoTokenResponse;
import com.gemmap.gemmap.auth.application.dto.kakao.KakaoUserInfoResponse;
import com.gemmap.gemmap.auth.application.dto.response.KakaoLoginResponseDto;
import com.gemmap.gemmap.auth.application.dto.response.KakaoTokenRefreshResponseDto;
import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtTokenDto;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.auth.infrastructure.oauth.KakaoOAuth2Service;
import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 인증 관련 비즈니스 로직을 처리하는 서비스 클래스
 *
 * 주요 기능:
 * - 소셜 로그인 (Kakao)
 * - 회원가입 (GUEST → USER 권한 전환)
 * - 토큰 갱신 (Access Token + Refresh Token Rotation)
 * - 로그아웃
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final KakaoOAuth2Service kakaoOAuth2Service;

    /**
     * 카카오 인가 URL 생성
     */
    public String getKakaoAuthorizationUrl() {
        try {
            return kakaoOAuth2Service.getAuthorizationUrl();
        } catch (Exception e) {
            log.error("카카오 인가 URL 생성 실패: {}", e.getMessage());
            throw new CommonException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }
    }

    /**
    * 카카오 로그인 처리 (인가 코드로 로그인)
    */
    @Transactional
    public KakaoLoginResponseDto kakaoLogin(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            KakaoTokenResponse tokenResponse = kakaoOAuth2Service.getAccessToken(authorizationCode);
            KakaoUserInfoResponse userInfo = kakaoOAuth2Service.getUserInfo(tokenResponse.getAccessToken());

            if (userInfo.getId() == null) {
                throw new CommonException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }

            String socialId = userInfo.getId().toString();
            String email = userInfo.getKakaoAccount() != null ? userInfo.getKakaoAccount().getEmail() : null;
            User user = findOrCreateKakaoUser(socialId, email, userInfo);

            JwtTokenDto jwtTokenDto = jwtUtil.generateTokens(user.getId(), user.getRole());
            user.updateRefreshToken(jwtTokenDto.getRefreshToken());
            user.updateLoginStatus(true);

            log.info("카카오 로그인 성공 - 사용자 ID: {}, 권한: {}", user.getId(), user.getRole());

            return KakaoLoginResponseDto.of(
                    jwtTokenDto.getAccessToken(),
                    jwtTokenDto.getRefreshToken(),
                    user.getRole(),
                    user.getId()
            );
        } catch (CommonException e) {
            // CommonException은 그대로 재던지기
            throw e;
        } catch (Exception e) {
            log.error("카카오 로그인 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 카카오 사용자 조회 또는 생성
     */
    private User findOrCreateKakaoUser(String socialId, String email, KakaoUserInfoResponse userInfo) {
        if (socialId == null || socialId.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            Optional<User> userOpt = userRepository.findBySocialIdAndProvider(socialId, EProvider.KAKAO);

            if (userOpt.isPresent()) {
                User existingUser = userOpt.get();
                // 기존 사용자의 카카오 정보 업데이트
                updateKakaoUserInfo(existingUser, userInfo);
                log.info("기존 카카오 사용자 로그인 - 사용자 ID: {}", existingUser.getId());
                return existingUser;
            }

            // 신규 사용자 생성
            User newUser = createKakaoUser(socialId, email, userInfo);
            userRepository.save(newUser);
            log.info("신규 카카오 사용자 생성 - 소셜 ID: {}", socialId);
            return newUser;
        } catch (Exception e) {
            log.error("카카오 사용자 처리 중 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.DATABASE_ERROR);
        }
    }

    /**
     * 카카오 사용자 생성
     */
    private User createKakaoUser(String socialId, String email, KakaoUserInfoResponse userInfo) {
        KakaoUserInfoResponse.KakaoAccount account = userInfo.getKakaoAccount();

        return User.builder()
                .socialId(socialId)
                .eProvider(EProvider.KAKAO)
                .role(ERole.USER)
                .email(email)
                .nickname(extractNickname(userInfo))
                .profileImage(extractProfileImage(userInfo))
                .build();
    }

    /**
     * 기존 카카오 사용자 정보 업데이트
     */
    private void updateKakaoUserInfo(User user, KakaoUserInfoResponse userInfo) {
        KakaoUserInfoResponse.KakaoAccount account = userInfo.getKakaoAccount();

        user.updateKakaoUserInfo(
                extractNickname(userInfo),
                extractProfileImage(userInfo)
        );
    }

    /**
     * 카카오 사용자 정보에서 값 추출 (우선순위: kakao_account.profile > properties)
     */
    private <T> T extractFromKakaoUserInfo(KakaoUserInfoResponse userInfo,
                                         java.util.function.Function<KakaoUserInfoResponse.KakaoAccount.Profile, T> profileExtractor,
                                         java.util.function.Function<KakaoUserInfoResponse.Properties, T> propertiesExtractor,
                                         T defaultValue) {
        // kakao_account.profile에서 추출 시도
        if (userInfo.getKakaoAccount() != null &&
            userInfo.getKakaoAccount().getProfile() != null) {
            T profileValue = profileExtractor.apply(userInfo.getKakaoAccount().getProfile());
            if (profileValue != null) {
                return profileValue;
            }
        }

        // properties에서 추출 시도
        if (userInfo.getProperties() != null) {
            T propertiesValue = propertiesExtractor.apply(userInfo.getProperties());
            if (propertiesValue != null) {
                return propertiesValue;
            }
        }

        return defaultValue;
    }

    /**
     * 닉네임 추출 (우선순위: kakao_account.profile > properties)
     */
    private String extractNickname(KakaoUserInfoResponse userInfo) {
        return extractFromKakaoUserInfo(userInfo,
                profile -> profile.getNickname(),
                properties -> properties.getNickname(),
                null);
    }

    /**
     * 프로필 이미지 추출 (우선순위: kakao_account.profile > properties)
     */
    private String extractProfileImage(KakaoUserInfoResponse userInfo) {
        return extractFromKakaoUserInfo(userInfo,
                profile -> profile.getProfileImageUrl(),
                properties -> properties.getProfileImage(),
                Constant.DEFAULT_PROFILE_IMAGE);
    }


    /**
     * Access Token 갱신 (userId 기반)
     */
    @Transactional
    public JwtTokenDto refreshAccessToken(Long userId) {
        try {
            // 사용자 조회 및 로그인 상태 확인
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

            // 사용자 로그인 상태 확인
            if (!user.isLogin()) {
                throw new CommonException(ErrorCode.USER_NOT_LOGGED_IN);
            }

            // 사용자의 Refresh Token 확인
            String refreshToken = user.getRefreshToken();
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            // Refresh Token 유효성 검증
            if (!jwtUtil.validateToken(refreshToken)) {
                throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            // Refresh Token 만료 임박 시 새로 발급 (Token Rotation)
            boolean shouldRotateRefreshToken = jwtUtil.isTokenExpiringSoon(
                    refreshToken,
                    (long) (jwtUtil.getRefreshTokenExpiration() * Constant.REFRESH_TOKEN_ROTATION_THRESHOLD)
            );

            // 새 Access Token 발급
            String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole());

            // Refresh Token 갱신 (Token Rotation 적용)
            String newRefreshToken = refreshToken;
            if (shouldRotateRefreshToken) {
                newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getRole());
                user.updateRefreshToken(newRefreshToken);
            }

            log.info("토큰 갱신 완료 - 사용자 ID: {}, Refresh Token 갱신: {}",
                    user.getId(), shouldRotateRefreshToken);

            return JwtTokenDto.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();

        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("토큰 갱신 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Access Token 갱신 (KakaoLoginResponseDto 형태로 반환)
     */
    @Transactional
    public KakaoLoginResponseDto refreshAccessTokenWithUserInfo(Long userId) {
        try {
            // 사용자 조회 및 로그인 상태 확인
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

            // 사용자 로그인 상태 확인
            if (!user.isLogin()) {
                throw new CommonException(ErrorCode.USER_NOT_LOGGED_IN);
            }

            // 사용자의 Refresh Token 확인
            String refreshToken = user.getRefreshToken();
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            // Refresh Token 유효성 검증
            if (!jwtUtil.validateToken(refreshToken)) {
                throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            // Refresh Token 만료 임박 시 새로 발급 (Token Rotation)
            boolean shouldRotateRefreshToken = jwtUtil.isTokenExpiringSoon(
                    refreshToken,
                    (long) (jwtUtil.getRefreshTokenExpiration() * Constant.REFRESH_TOKEN_ROTATION_THRESHOLD)
            );

            // 새 Access Token 발급
            String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole());

            // Refresh Token 갱신 (Token Rotation 적용)
            String newRefreshToken = refreshToken;
            if (shouldRotateRefreshToken) {
                newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getRole());
                user.updateRefreshToken(newRefreshToken);
            }

            log.info("토큰 갱신 완료 - 사용자 ID: {}, Refresh Token 갱신: {}",
                    user.getId(), shouldRotateRefreshToken);

            return KakaoLoginResponseDto.of(
                    newAccessToken,
                    newRefreshToken,
                    user.getRole(),
                    user.getId()
            );

        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("토큰 갱신 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 단순 서비스 로그아웃 처리
     * 클라이언트에서 토큰을 삭제하도록 하는 단순한 응답
     */
    public void simpleLogout(Long userId) {
        try {
            // 사용자 존재 확인 (보안상 필요)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

            log.info("서비스 로그아웃 - 사용자 ID: {}", userId);
            // 실제로는 클라이언트에서 토큰을 삭제하도록 204 응답만 전송

        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("로그아웃 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}