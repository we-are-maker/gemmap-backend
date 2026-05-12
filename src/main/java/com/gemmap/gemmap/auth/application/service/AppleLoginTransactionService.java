package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.application.dto.response.SocialLoginResponseDto;
import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtTokenDto;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleLoginTransactionService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public SocialLoginResponseDto completeAppleLogin(
            String socialId, String email, String name, String encryptedAppleRefreshToken
    ) {
        User user = findOrCreateAppleUser(socialId, email, name);

        user.updateAppleRefreshToken(encryptedAppleRefreshToken);

        JwtTokenDto jwtTokenDto = jwtUtil.generateTokens(user.getId(), user.getRole());
        user.updateRefreshToken(jwtTokenDto.getRefreshToken());
        user.updateLoginStatus(true);

        log.info("Apple 로그인 성공 - 사용자 ID: {}, 권한: {}", user.getId(), user.getRole());

        return SocialLoginResponseDto.of(
                user.getId(),
                user.getRole(),
                jwtTokenDto.getAccessToken(),
                jwtTokenDto.getRefreshToken()
        );
    }

    private User findOrCreateAppleUser(String socialId, String email, String name) {
        if (socialId == null || socialId.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return userRepository.findBySocialIdAndProvider(socialId, EProvider.APPLE)
                .orElseGet(() -> createAppleUser(socialId, email, name));
    }

    private User createAppleUser(String socialId, String email, String name) {
        validateAppleSignupInfo(email, name);

        User newUser = User.builder()
                .socialId(socialId)
                .eProvider(EProvider.APPLE)
                .role(ERole.GUEST)
                .email(email)
                .name(name)
                .nickname(null)
                .profileImage(null)
                .gender(null)
                .ageRange(null)
                .birthday(null)
                .birthyear(null)
                .build();

        try {
            User savedUser = userRepository.save(newUser);
            log.info("신규 Apple 사용자 생성 - 소셜 ID: {}, 사용자 ID: {}", socialId, savedUser.getId());
            return savedUser;
        } catch (DataIntegrityViolationException e) {
            log.warn("Apple 사용자 동시 생성 충돌 - socialId: {}, provider: APPLE. 기존 사용자 재조회", socialId);
            return userRepository.findBySocialIdAndProvider(socialId, EProvider.APPLE)
                    .orElseThrow(() -> new CommonException(ErrorCode.DATABASE_ERROR));
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple 사용자 생성 중 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.DATABASE_ERROR);
        }
    }

    private void validateAppleSignupInfo(String email, String name) {
        if (name == null || name.trim().isEmpty() || email == null || email.trim().isEmpty()) {
            throw new CommonException(ErrorCode.APPLE_REQUIRED_USER_INFO_MISSING);
        }
    }
}
