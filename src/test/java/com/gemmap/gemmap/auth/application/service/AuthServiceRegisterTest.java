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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    @DisplayName("카카오 USER 재로그인 — register 확정 gender/birthDate 보호")
    class KakaoUserRelogin {

        @Test
        @DisplayName("USER 재로그인 시 카카오 응답 null 이어도 register 확정 gender/birthDate 유지")
        void userRolePreservesGenderAndBirthDate() {
            EGender registeredGender = EGender.MALE;
            LocalDate registeredBirthDate = LocalDate.of(2000, 1, 1);

            User user = mock(User.class);
            given(user.getRole()).willReturn(ERole.USER);
            given(user.getGender()).willReturn(registeredGender);
            given(user.getBirthDate()).willReturn(registeredBirthDate);
            given(user.getId()).willReturn(TEST_USER_ID);

            KakaoAccessTokenInfoResponse tokenInfo = mock(KakaoAccessTokenInfoResponse.class);
            given(tokenInfo.getId()).willReturn(12345L);

            KakaoUserInfoResponse.KakaoAccount account = mock(KakaoUserInfoResponse.KakaoAccount.class);
            given(account.getGender()).willReturn(null);    // 카카오 응답: 성별 null
            given(account.getBirthday()).willReturn(null);  // 카카오 응답: 생일 null
            given(account.getBirthyear()).willReturn(null); // 카카오 응답: 연도 null
            given(account.getEmail()).willReturn("test@example.com");

            KakaoUserInfoResponse userInfo = mock(KakaoUserInfoResponse.class);
            given(userInfo.getKakaoAccount()).willReturn(account);

            given(kakaoOAuth2Service.getAccessTokenInfo("kakao-token")).willReturn(tokenInfo);
            given(kakaoOAuth2Service.getUserInfo("kakao-token")).willReturn(userInfo);
            given(userRepository.findBySocialIdAndProvider("12345", EProvider.KAKAO)).willReturn(Optional.of(user));
            given(userRepository.save(user)).willReturn(user);
            given(jwtUtil.generateTokens(anyLong(), any(ERole.class))).willReturn(
                    JwtTokenDto.builder().accessToken("at").refreshToken("rt").build()
            );

            authService.authenticateWithKakaoAccessToken("kakao-token");

            ArgumentCaptor<EGender> genderCaptor = ArgumentCaptor.forClass(EGender.class);
            ArgumentCaptor<LocalDate> birthDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(user).updateKakaoUserInfo(
                    any(), any(), any(),
                    genderCaptor.capture(),
                    any(),
                    birthDateCaptor.capture()
            );
            assertThat(genderCaptor.getValue()).isEqualTo(registeredGender);
            assertThat(birthDateCaptor.getValue()).isEqualTo(registeredBirthDate);
        }

        @Test
        @DisplayName("GUEST 재로그인 시 카카오 응답 값으로 gender/birthDate 최신화")
        void guestRoleUpdatesGenderAndBirthDate() {
            User user = mock(User.class);
            given(user.getRole()).willReturn(ERole.GUEST);
            given(user.getId()).willReturn(TEST_USER_ID);

            KakaoAccessTokenInfoResponse tokenInfo = mock(KakaoAccessTokenInfoResponse.class);
            given(tokenInfo.getId()).willReturn(12345L);

            KakaoUserInfoResponse.KakaoAccount account = mock(KakaoUserInfoResponse.KakaoAccount.class);
            given(account.getGender()).willReturn("female");  // 카카오 응답: female
            given(account.getBirthday()).willReturn("0315");  // 카카오 응답: 생일
            given(account.getBirthyear()).willReturn("1995"); // 카카오 응답: 연도
            given(account.getEmail()).willReturn("test@example.com");

            KakaoUserInfoResponse userInfo = mock(KakaoUserInfoResponse.class);
            given(userInfo.getKakaoAccount()).willReturn(account);

            given(kakaoOAuth2Service.getAccessTokenInfo("kakao-token")).willReturn(tokenInfo);
            given(kakaoOAuth2Service.getUserInfo("kakao-token")).willReturn(userInfo);
            given(userRepository.findBySocialIdAndProvider("12345", EProvider.KAKAO)).willReturn(Optional.of(user));
            given(userRepository.save(user)).willReturn(user);
            given(jwtUtil.generateTokens(anyLong(), any(ERole.class))).willReturn(
                    JwtTokenDto.builder().accessToken("at").refreshToken("rt").build()
            );

            authService.authenticateWithKakaoAccessToken("kakao-token");

            ArgumentCaptor<EGender> genderCaptor = ArgumentCaptor.forClass(EGender.class);
            ArgumentCaptor<LocalDate> birthDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
            verify(user).updateKakaoUserInfo(
                    any(), any(), any(),
                    genderCaptor.capture(),
                    any(),
                    birthDateCaptor.capture()
            );
            assertThat(genderCaptor.getValue()).isEqualTo(EGender.FEMALE);
            assertThat(birthDateCaptor.getValue()).isEqualTo(LocalDate.of(1995, 3, 15));
        }
    }
}
