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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleLoginTransactionService {

    @Value("${withdraw.rejoin-grace-period-days:30}")
    private int rejoinGracePeriodDays;

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

        // 1) 활성 사용자 조회
        Optional<User> activeUser = userRepository.findBySocialIdAndProvider(socialId, EProvider.APPLE);
        if (activeUser.isPresent()) {
            return activeUser.get();
        }

        // 2) soft-deleted 사용자 조회 → 재가입 분기
        //    Apple 공식 동작상 재로그인 시 name/email은 null로 와도 정상.
        //    복구 경로에서는 validateAppleSignupInfo 우회 + 기존 DB 값 유지.
        Optional<User> softDeleted = userRepository.findSoftDeletedBySocialIdAndProvider(socialId, EProvider.APPLE);
        if (softDeleted.isPresent()) {
            User user = softDeleted.get();
            LocalDate originalDeleteDate = user.getDeleteDate();   // recoverUser() 전에 보관
            if (originalDeleteDate == null) {
                log.warn("soft-deleted Apple 사용자의 delete_date가 null — 데이터 정합성 점검 필요. 유예 경과로 간주");
            }
            boolean expired = user.isGracePeriodExpired(rejoinGracePeriodDays);

            user.recoverUser();
            // Apple은 복구 경로에서 email/name 갱신하지 않음 (Apple SDK가 재로그인 시 제공 안 함 — 공식 동작)
            if (expired) {
                user.resetForRejoin();
                log.info("Apple 재가입 — 유예 경과 초기화: socialId={}, originalDeleteDate={}", socialId, originalDeleteDate);
            } else {
                user.updateRole(ERole.GUEST);       // 유예 내 재가입도 회원가입 화면 재진입(role만 리셋, 그 외 데이터 보존)
                log.info("Apple 재가입 — 유예 내 복구(GUEST 리셋): socialId={}, originalDeleteDate={}", socialId, originalDeleteDate);
            }
            return user;   // @Transactional 영속 컨텍스트가 변경 자동 반영
        }

        // 3) 완전 신규 가입
        return createAppleUser(socialId, email, name);
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
