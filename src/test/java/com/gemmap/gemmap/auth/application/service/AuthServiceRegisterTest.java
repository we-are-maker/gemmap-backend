package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.application.dto.kakao.KakaoAccessTokenInfoResponse;
import com.gemmap.gemmap.auth.application.dto.kakao.KakaoUserInfoResponse;
import com.gemmap.gemmap.auth.application.dto.response.RegisterProfileResponseDto;
import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtTokenDto;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.auth.infrastructure.oauth.AppleOAuth2Service;
import com.gemmap.gemmap.auth.infrastructure.oauth.KakaoOAuth2Service;
import com.gemmap.gemmap.shared.common.enums.EGender;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3PresignedUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;

/**
 * 회원가입 프리필 조회(getRegisterProfile) 및 회원가입(register) 서비스 단위 테스트.
 * 카카오 USER 재로그인 시 register 확정 gender/birthDate 보호도 함께 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    private static final Long TEST_USER_ID = 1L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private KakaoOAuth2Service kakaoOAuth2Service;
    @Mock
    private AppleOAuth2Service appleOAuth2Service;
    @Mock
    private AppleLoginTransactionService appleLoginTransactionService;
    @Mock
    private WithdrawTransactionService withdrawTransactionService;
    @Mock
    private TextEncryptor appleRefreshTokenEncryptor;
    @Mock
    private ObjectStorageService objectStorageService;
    @Mock
    private S3PresignedUrlService s3PresignedUrlService;
    @Mock
    private S3Properties s3Properties;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("getRegisterProfile — 프리필 조회")
    class GetRegisterProfile {

        @Test
        @DisplayName("존재하지 않는 userId 로 조회 시 USER_NOT_FOUND 예외")
        void userNotFound() {
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.getRegisterProfile(TEST_USER_ID))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("profileImage null 인 경우 profileImage null 반환 (presign 호출 없음)")
        void nullProfileImage() {
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.getProfileImage()).willReturn(null);
            given(user.getNickname()).willReturn("nick");
            given(user.getBirthDate()).willReturn(LocalDate.of(2000, 1, 1));
            given(user.getGender()).willReturn(EGender.MALE);

            RegisterProfileResponseDto result = authService.getRegisterProfile(TEST_USER_ID);

            assertThat(result.profileImage()).isNull();
            verify(s3PresignedUrlService, never()).resolveProfileImageUrl(any());
        }

        @Test
        @DisplayName("default.png 인 경우 profileImage null 반환 (presign 호출 없음)")
        void defaultProfileImage() {
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.getProfileImage()).willReturn("default.png");
            given(user.getNickname()).willReturn("nick");
            given(user.getBirthDate()).willReturn(null);
            given(user.getGender()).willReturn(null);

            RegisterProfileResponseDto result = authService.getRegisterProfile(TEST_USER_ID);

            assertThat(result.profileImage()).isNull();
            verify(s3PresignedUrlService, never()).resolveProfileImageUrl(any());
        }

        @Test
        @DisplayName("외부 URL(카카오 이미지 등) 인 경우 resolveProfileImageUrl 호출 후 반환")
        void externalUrl() {
            User user = mock(User.class);
            String kakaoUrl = "http://k.kakaocdn.net/image.jpg";
            String resolved = "https://presigned.example.com/image.jpg";
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.getProfileImage()).willReturn(kakaoUrl);
            given(user.getNickname()).willReturn("nick");
            given(user.getBirthDate()).willReturn(null);
            given(user.getGender()).willReturn(null);
            given(s3PresignedUrlService.resolveProfileImageUrl(kakaoUrl)).willReturn(resolved);

            RegisterProfileResponseDto result = authService.getRegisterProfile(TEST_USER_ID);

            assertThat(result.profileImage()).isEqualTo(resolved);
        }
    }

    @Nested
    @DisplayName("register — 생년월일/성별 null=keep")
    class Register {

        private void setupBaseMocks(User user) {
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.isLogin()).willReturn(true);
            given(user.getRole()).willReturn(ERole.GUEST, ERole.USER);
            given(user.getNickname()).willReturn("existingNick");
            given(user.getProfileImage()).willReturn("default.png");
            given(user.getId()).willReturn(TEST_USER_ID);
            given(userRepository.save(user)).willReturn(user);
            given(jwtUtil.generateTokens(anyLong(), any(ERole.class))).willReturn(
                    JwtTokenDto.builder().accessToken("at").refreshToken("rt").build()
            );
        }

        @Test
        @DisplayName("birthDate/gender null 전달 시 엔티티에 null 그대로 전달 (null=keep은 엔티티 책임)")
        void nullBirthDateAndGender_passNullToEntity() {
            User user = mock(User.class);
            setupBaseMocks(user);

            authService.register(TEST_USER_ID, null, null, null, null);

            verify(user).updateBirthDate(null);
            verify(user).updateGender(null);
        }

        @Test
        @DisplayName("birthDate/gender 값 전달 시 해당 값을 엔티티에 전달")
        void withBirthDateAndGender_passValueToEntity() {
            User user = mock(User.class);
            setupBaseMocks(user);
            LocalDate birthDate = LocalDate.of(1995, 3, 15);
            EGender gender = EGender.FEMALE;

            authService.register(TEST_USER_ID, null, birthDate, gender, null);

            verify(user).updateBirthDate(birthDate);
            verify(user).updateGender(gender);
        }
    }

    @Nested
    @DisplayName("register — 입력 검증")
    class RegisterValidation {

        @Test
        @DisplayName("닉네임 미입력 + 기존 닉네임 없을 때 INVALID_INPUT_VALUE 예외")
        void nullNickname_withNoExistingNickname_throwsInvalidInputValue() {
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.isLogin()).willReturn(true);
            given(user.getRole()).willReturn(ERole.GUEST);
            given(user.getNickname()).willReturn(null);

            assertThatThrownBy(() -> authService.register(TEST_USER_ID, null, null, null, null))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("미래 생년월일 입력 시 INVALID_INPUT_VALUE 예외")
        void futureBirthDate_throwsInvalidInputValue() {
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.isLogin()).willReturn(true);
            given(user.getRole()).willReturn(ERole.GUEST);
            given(user.getNickname()).willReturn("existingNick");

            LocalDate futureBirthDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

            assertThatThrownBy(() -> authService.register(TEST_USER_ID, null, futureBirthDate, null, null))
                    .isInstanceOf(CommonException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Nested
    @DisplayName("카카오 활성 사용자 재로그인 — register 확정 정보 보호")
    class KakaoActiveUserRelogin {

        private void setupActiveReloginMocks(User user, ERole role) {
            given(user.getRole()).willReturn(role);
            given(user.getId()).willReturn(TEST_USER_ID);

            KakaoAccessTokenInfoResponse tokenInfo = mock(KakaoAccessTokenInfoResponse.class);
            given(tokenInfo.getId()).willReturn(12345L);

            KakaoUserInfoResponse userInfo = mock(KakaoUserInfoResponse.class);
            given(userInfo.getKakaoAccount()).willReturn(null);

            given(kakaoOAuth2Service.getAccessTokenInfo("kakao-token")).willReturn(tokenInfo);
            given(kakaoOAuth2Service.getUserInfo("kakao-token")).willReturn(userInfo);
            given(userRepository.findBySocialIdAndProvider("12345", EProvider.KAKAO)).willReturn(Optional.of(user));
            given(jwtUtil.generateTokens(anyLong(), any(ERole.class))).willReturn(
                    JwtTokenDto.builder().accessToken("at").refreshToken("rt").build()
            );
        }

        @Test
        @DisplayName("USER 재로그인 시 birthDate/gender 갱신 없음 — register 확정 값 보호")
        void userRole_noProfileUpdate() {
            User user = mock(User.class);
            setupActiveReloginMocks(user, ERole.USER);

            authService.authenticateWithKakaoAccessToken("kakao-token");

            verify(user, never()).updateBirthDate(any());
            verify(user, never()).updateGender(any());
        }

        @Test
        @DisplayName("GUEST 재로그인 시 birthDate/gender 갱신 없음 — register에서 최종 확정")
        void guestRole_noProfileUpdate() {
            User user = mock(User.class);
            setupActiveReloginMocks(user, ERole.GUEST);

            authService.authenticateWithKakaoAccessToken("kakao-token");

            verify(user, never()).updateBirthDate(any());
            verify(user, never()).updateGender(any());
        }
    }

    @Nested
    @DisplayName("카카오 soft-deleted 재가입 — 유예 내/경과 분기")
    class KakaoSoftDeletedRejoin {

        private void setupSoftDeletedMocks(User softDeletedUser) {
            KakaoAccessTokenInfoResponse tokenInfo = mock(KakaoAccessTokenInfoResponse.class);
            given(tokenInfo.getId()).willReturn(12345L);

            KakaoUserInfoResponse userInfo = mock(KakaoUserInfoResponse.class);
            given(userInfo.getKakaoAccount()).willReturn(null);

            given(kakaoOAuth2Service.getAccessTokenInfo("kakao-token")).willReturn(tokenInfo);
            given(kakaoOAuth2Service.getUserInfo("kakao-token")).willReturn(userInfo);
            given(userRepository.findBySocialIdAndProvider("12345", EProvider.KAKAO)).willReturn(Optional.empty());
            given(userRepository.findSoftDeletedBySocialIdAndProvider("12345", EProvider.KAKAO))
                    .willReturn(Optional.of(softDeletedUser));
            given(userRepository.save(softDeletedUser)).willReturn(softDeletedUser);
            given(softDeletedUser.getId()).willReturn(TEST_USER_ID);
            given(softDeletedUser.getRole()).willReturn(ERole.GUEST);
            given(jwtUtil.generateTokens(anyLong(), any(ERole.class))).willReturn(
                    JwtTokenDto.builder().accessToken("at").refreshToken("rt").build()
            );
        }

        @Test
        @DisplayName("유예 내 재가입 — recoverUser 호출, resetForRejoin 미호출")
        void withinGrace_recoverOnly() {
            User user = mock(User.class);
            given(user.getDeleteDate()).willReturn(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(10));
            given(user.isGracePeriodExpired(any(int.class))).willReturn(false);
            setupSoftDeletedMocks(user);

            authService.authenticateWithKakaoAccessToken("kakao-token");

            verify(user).recoverUser();
            verify(user, never()).resetForRejoin();
        }

        @Test
        @DisplayName("유예 경과 재가입 — recoverUser 후 resetForRejoin 호출 (순서 보장)")
        void expiredGrace_recoverThenReset() {
            User user = mock(User.class);
            given(user.getDeleteDate()).willReturn(LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(40));
            given(user.isGracePeriodExpired(any(int.class))).willReturn(true);
            setupSoftDeletedMocks(user);

            authService.authenticateWithKakaoAccessToken("kakao-token");

            var order = inOrder(user);
            order.verify(user).recoverUser();
            order.verify(user).resetForRejoin();
        }
    }
}
