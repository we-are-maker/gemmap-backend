package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.application.dto.response.PhotoConsentResponse;
import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.auth.infrastructure.oauth.AppleOAuth2Service;
import com.gemmap.gemmap.auth.infrastructure.oauth.KakaoOAuth2Service;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link AuthService#setPhotoConsent(Long, boolean)} 단위 테스트.
 *
 * 본 기능(worklog/16_photo_consent_revoke) 에서 추가된 양방향 멱등 분기를 검증한다.
 * AuthService 의 다른 메서드(OAuth, JWT 등) 와는 분리하여 본 책임만 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServicePhotoConsentTest {

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
    @DisplayName("setPhotoConsent — agreed=true (동의)")
    class AgreeCases {

        @Test
        @DisplayName("미동의 사용자가 동의 요청 시 agreeToPhotoConsent() 호출 + 응답 agreed=true")
        void agreeWhenNotConsented() {
            // given
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.hasPhotoConsentAgreed()).willReturn(false, true);

            // when
            PhotoConsentResponse response = authService.setPhotoConsent(TEST_USER_ID, true);

            // then
            verify(user).agreeToPhotoConsent();
            verify(user, never()).revokePhotoConsent();
            assertThat(response.agreed()).isTrue();
        }

        @Test
        @DisplayName("이미 동의한 사용자 재요청 시 agreeToPhotoConsent() 호출 없음 (멱등) + 응답 agreed=true")
        void agreeWhenAlreadyConsented() {
            // given
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.hasPhotoConsentAgreed()).willReturn(true);

            // when
            PhotoConsentResponse response = authService.setPhotoConsent(TEST_USER_ID, true);

            // then
            verify(user, never()).agreeToPhotoConsent();
            verify(user, never()).revokePhotoConsent();
            assertThat(response.agreed()).isTrue();
        }
    }

    @Nested
    @DisplayName("setPhotoConsent — agreed=false (철회)")
    class RevokeCases {

        @Test
        @DisplayName("동의 사용자가 철회 요청 시 revokePhotoConsent() 호출 + 응답 agreed=false")
        void revokeWhenConsented() {
            // given
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.hasPhotoConsentAgreed()).willReturn(true, false);

            // when
            PhotoConsentResponse response = authService.setPhotoConsent(TEST_USER_ID, false);

            // then
            verify(user).revokePhotoConsent();
            verify(user, never()).agreeToPhotoConsent();
            assertThat(response.agreed()).isFalse();
        }

        @Test
        @DisplayName("미동의/철회 사용자가 철회 재요청 시 revokePhotoConsent() 호출 없음 (멱등) + 응답 agreed=false")
        void revokeWhenAlreadyNotConsented() {
            // given
            User user = mock(User.class);
            given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.of(user));
            given(user.hasPhotoConsentAgreed()).willReturn(false);

            // when
            PhotoConsentResponse response = authService.setPhotoConsent(TEST_USER_ID, false);

            // then
            verify(user, never()).agreeToPhotoConsent();
            verify(user, never()).revokePhotoConsent();
            assertThat(response.agreed()).isFalse();
        }
    }

    @Test
    @DisplayName("존재하지 않는 userId 로 호출 시 USER_NOT_FOUND 예외")
    void userNotFound() {
        // given
        given(userRepository.findById(TEST_USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.setPhotoConsent(TEST_USER_ID, true))
                .isInstanceOf(CommonException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
