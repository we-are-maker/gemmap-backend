package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.application.dto.apple.AppleIdentityTokenClaims;
import com.gemmap.gemmap.auth.application.dto.apple.AppleTokenResponse;
import com.gemmap.gemmap.auth.application.dto.kakao.KakaoAccessTokenInfoResponse;
import com.gemmap.gemmap.auth.application.dto.kakao.KakaoUserInfoResponse;
import com.gemmap.gemmap.auth.application.dto.response.PhotoConsentResponse;
import com.gemmap.gemmap.auth.application.dto.response.RegisterResponseDto;
import com.gemmap.gemmap.auth.application.dto.response.SocialLoginResponseDto;
import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtTokenDto;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.auth.infrastructure.oauth.AppleOAuth2Service;
import com.gemmap.gemmap.auth.infrastructure.oauth.KakaoOAuth2Service;
import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3PresignedUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * 인증 관련 비즈니스 로직을 처리하는 서비스 클래스
 *
 * 주요 기능:
 * - 소셜 로그인 (Kakao, Apple)
 * - 회원가입 (GUEST → USER 권한 전환)
 * - 토큰 갱신 (Access Token + Refresh Token Rotation)
 * - 로그아웃
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Value("${withdraw.rejoin-grace-period-days:30}")
    private int rejoinGracePeriodDays;

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final KakaoOAuth2Service kakaoOAuth2Service;
    private final AppleOAuth2Service appleOAuth2Service;
    private final AppleLoginTransactionService appleLoginTransactionService;
    private final WithdrawTransactionService withdrawTransactionService;

    @Qualifier("appleRefreshTokenEncryptor")
    private final TextEncryptor appleRefreshTokenEncryptor;

    private final ObjectStorageService objectStorageService;
    private final S3PresignedUrlService s3PresignedUrlService;
    private final S3Properties s3Properties;

    /**
     * 카카오 Access Token으로 인증 (모바일 SDK 방식)
     * 모바일 앱에서 획득한 카카오 Access Token을 검증하고 서비스 JWT 발급
     */
    @Transactional
    public SocialLoginResponseDto authenticateWithKakaoAccessToken(String kakaoAccessToken) {
        if (kakaoAccessToken == null || kakaoAccessToken.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            // 1. 카카오 Access Token 검증 (app_id 확인 및 만료 검증)
            KakaoAccessTokenInfoResponse tokenInfo = kakaoOAuth2Service.getAccessTokenInfo(kakaoAccessToken);

            if (tokenInfo.getId() == null) {
                throw new CommonException(ErrorCode.EXTERNAL_SERVICE_ERROR);
            }

            // 2. 카카오 사용자 정보 조회
            KakaoUserInfoResponse userInfo = kakaoOAuth2Service.getUserInfo(kakaoAccessToken);

            String socialId = tokenInfo.getId().toString();
            String email = userInfo.getKakaoAccount() != null ? userInfo.getKakaoAccount().getEmail() : null;

            // 3. 사용자 조회 또는 생성
            User user = findOrCreateKakaoUser(socialId, email, userInfo);

            // 4. 서비스 JWT 토큰 발급
            JwtTokenDto jwtTokenDto = jwtUtil.generateTokens(user.getId(), user.getRole());
            user.updateRefreshToken(jwtTokenDto.getRefreshToken());
            user.updateLoginStatus(true);

            log.info("카카오 SDK 로그인 성공 - 사용자 ID: {}, 권한: {}", user.getId(), user.getRole());

            return SocialLoginResponseDto.of(
                    user.getId(),
                    user.getRole(),
                    jwtTokenDto.getAccessToken(),
                    jwtTokenDto.getRefreshToken()
            );
        } catch (CommonException e) {
            // CommonException은 그대로 재던지기
            throw e;
        } catch (Exception e) {
            log.error("카카오 SDK 로그인 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Apple Identity Token으로 인증 (모바일 SDK 방식)
     * 외부 Apple 토큰 검증·authorization_code 교환·암호화는 트랜잭션 밖에서 수행하고,
     * DB 작업만 AppleLoginTransactionService로 위임한다.
     */
    public SocialLoginResponseDto authenticateWithAppleToken(
            String identityToken, String email, String name, String authorizationCode
    ) {
        if (identityToken == null || identityToken.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1) identityToken 검증 — 외부 HTTP (JWKS)
        AppleIdentityTokenClaims claims = appleOAuth2Service.validateAndExtractClaims(identityToken);

        // 2) authorizationCode 교환 — 외부 HTTP (/auth/token)
        AppleTokenResponse tokenResponse = appleOAuth2Service.exchangeAuthorizationCode(authorizationCode);

        // 3) refresh_token 암호화
        String encryptedAppleRefreshToken = appleRefreshTokenEncryptor.encrypt(tokenResponse.refreshToken());

        // 4) DB 반영은 @Transactional 서비스로 위임
        return appleLoginTransactionService.completeAppleLogin(
                claims.sub(), email, name, encryptedAppleRefreshToken
        );
    }

    /**
     * 카카오 사용자 조회 또는 생성
     */
    private User findOrCreateKakaoUser(String socialId, String email, KakaoUserInfoResponse userInfo) {
        if (socialId == null || socialId.trim().isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1) 활성 사용자 조회 → 기존 로그인
        Optional<User> activeUser = userRepository.findBySocialIdAndProvider(socialId, EProvider.KAKAO);
        if (activeUser.isPresent()) {
            User existing = activeUser.get();
            updateKakaoUserInfo(existing, userInfo);
            User saved = userRepository.save(existing);
            log.info("기존 카카오 사용자 정보 업데이트 - 사용자 ID: {}", saved.getId());
            return saved;
        }

        // 2) soft-deleted 사용자 조회 → 재가입 분기
        Optional<User> softDeleted = userRepository.findSoftDeletedBySocialIdAndProvider(socialId, EProvider.KAKAO);
        if (softDeleted.isPresent()) {
            User user = softDeleted.get();
            LocalDate originalDeleteDate = user.getDeleteDate();   // recoverUser() 전에 보관
            if (originalDeleteDate == null) {
                log.warn("soft-deleted 사용자의 delete_date가 null — 데이터 정합성 점검 필요. 유예 경과로 간주");
            }
            boolean expired = user.isGracePeriodExpired(rejoinGracePeriodDays);

            user.recoverUser();
            updateKakaoUserInfo(user, userInfo);   // 소셜 리니어블 최신화 먼저

            if (expired) {
                user.resetForRejoin();              // 마지막에 권한·임의 프로필 초기화
                log.info("카카오 재가입 — 유예 경과 초기화: socialId={}, originalDeleteDate={}", socialId, originalDeleteDate);
            } else {
                log.info("카카오 재가입 — 유예 내 복구: socialId={}, originalDeleteDate={}", socialId, originalDeleteDate);
            }
            return userRepository.save(user);
        }

        // 3) 완전 신규 가입
        try {
            User newUser = createKakaoUser(socialId, email, userInfo);
            User saved = userRepository.save(newUser);
            log.info("신규 카카오 사용자 생성 - 소셜 ID: {}, 사용자 ID: {}", socialId, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 동시 재로그인 충돌 시 재조회로 수렴 (Apple 패턴 일관 적용)
            log.warn("카카오 사용자 동시 생성 충돌 - socialId: {}. 활성/탈퇴 재조회", socialId);
            return userRepository.findBySocialIdAndProvider(socialId, EProvider.KAKAO)
                    .or(() -> userRepository.findSoftDeletedBySocialIdAndProvider(socialId, EProvider.KAKAO))
                    .orElseThrow(() -> new CommonException(ErrorCode.DATABASE_ERROR));
        }
    }


    /**
     * 카카오 사용자 생성
     * 신규 사용자는 GUEST 권한으로 생성
     */
    private User createKakaoUser(String socialId, String email, KakaoUserInfoResponse userInfo) {
        KakaoUserInfoResponse.KakaoAccount account = userInfo.getKakaoAccount();

        String name = extractName(userInfo);
        String nickname = extractNickname(userInfo);
        String profileImage = extractProfileImage(userInfo);
        String gender = extractGender(userInfo);
        String ageRange = extractAgeRange(userInfo);
        String birthday = extractBirthday(userInfo);
        String birthyear = extractBirthyear(userInfo);

        log.info("신규 카카오 사용자 생성 준비 - Email: {}, Name: {}, Nickname: {}, Gender: {}, AgeRange: {}, Birthday: {}, Birthyear: {}",
                email, name, nickname, gender, ageRange, birthday, birthyear);

        return User.builder()
                .socialId(socialId)
                .eProvider(EProvider.KAKAO)
                .role(ERole.GUEST)
                .email(email)
                .name(name)
                .nickname(nickname)
                .profileImage(profileImage)
                .gender(gender)
                .ageRange(ageRange)
                .birthday(birthday)
                .birthyear(birthyear)
                .build();
    }

    /**
     * 기존 카카오 사용자 정보 업데이트
     */
    private void updateKakaoUserInfo(User user, KakaoUserInfoResponse userInfo) {
        KakaoUserInfoResponse.KakaoAccount account = userInfo.getKakaoAccount();

        String name = extractName(userInfo);
        String nickname = extractNickname(userInfo);
        String profileImage = extractProfileImage(userInfo);
        String gender = extractGender(userInfo);
        String ageRange = extractAgeRange(userInfo);
        String birthday = extractBirthday(userInfo);
        String birthyear = extractBirthyear(userInfo);

        log.info("기존 카카오 사용자 정보 업데이트 준비 - UserID: {}, Name: {}, Nickname: {}, Gender: {}, AgeRange: {}, Birthday: {}, Birthyear: {}",
                user.getId(), name, nickname, gender, ageRange, birthday, birthyear);

        user.updateKakaoUserInfo(
                name,
                nickname,
                profileImage,
                gender,
                ageRange,
                birthday,
                birthyear
        );

        // FR-3: 소셜 최신 email 갱신 (기존 updateKakaoUserInfo는 email을 다루지 않음)
        String email = account != null ? account.getEmail() : null;
        user.updateEmail(email);
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
     * 이름 추출 (kakao_account.name)
     */
    private String extractName(KakaoUserInfoResponse userInfo) {
        if (userInfo.getKakaoAccount() != null) {
            String name = userInfo.getKakaoAccount().getName();
            return name;
        }
        log.debug("카카오 계정 정보 없음 - 이름 추출 실패");
        return null;
    }

    /**
     * 성별 추출 (kakao_account.gender)
     */
    private String extractGender(KakaoUserInfoResponse userInfo) {
        if (userInfo.getKakaoAccount() != null) {
            String gender = userInfo.getKakaoAccount().getGender();
            return gender;
        }
        log.debug("카카오 계정 정보 없음 - 성별 추출 실패");
        return null;
    }

    /**
     * 연령대 추출 (kakao_account.age_range)
     */
    private String extractAgeRange(KakaoUserInfoResponse userInfo) {
        if (userInfo.getKakaoAccount() != null) {
            String ageRange = userInfo.getKakaoAccount().getAgeRange();
            return ageRange;
        }
        log.debug("카카오 계정 정보 없음 - 연령대 추출 실패");
        return null;
    }

    /**
     * 생일 추출 (kakao_account.birthday)
     */
    private String extractBirthday(KakaoUserInfoResponse userInfo) {
        if (userInfo.getKakaoAccount() != null) {
            String birthday = userInfo.getKakaoAccount().getBirthday();
            return birthday;
        }
        log.debug("카카오 계정 정보 없음 - 생일 추출 실패");
        return null;
    }

    /**
     * 출생연도 추출 (kakao_account.birthyear)
     */
    private String extractBirthyear(KakaoUserInfoResponse userInfo) {
        if (userInfo.getKakaoAccount() != null) {
            String birthyear = userInfo.getKakaoAccount().getBirthyear();
            return birthyear;
        }
        log.debug("카카오 계정 정보 없음 - 출생연도 추출 실패");
        return null;
    }

    /**
     * 회원가입 처리 (GUEST → USER 권한 전환)
     * 닉네임과 프로필 이미지를 업데이트하고 권한을 USER로 변경
     */
    @Transactional
    public RegisterResponseDto register(Long userId, String nickname, MultipartFile profileImage) {
        try {
            // 1. 사용자 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

            // 2. 사용자 로그인 상태 확인
            if (!user.isLogin()) {
                throw new CommonException(ErrorCode.USER_NOT_LOGGED_IN);
            }

            // 3. 이미 USER 권한인지 확인
            if (user.getRole() == ERole.USER) {
                throw new CommonException(ErrorCode.ALREADY_REGISTERED_USER);
            }

            // 4. 닉네임 처리 (입력하지 않은 경우 카카오 닉네임 유지)
            String finalNickname = (nickname != null && !nickname.trim().isEmpty())
                    ? nickname
                    : user.getNickname();

            // 5. 프로필 이미지 처리 (입력하지 않은 경우 카카오 프로필 이미지 유지)
            String finalProfileImage = user.getProfileImage();
            if (profileImage != null && !profileImage.isEmpty()) {
                // 프로필 이미지 업로드
                finalProfileImage = uploadProfileImage(profileImage);
            }

            // 6. 사용자 정보 업데이트
            user.updateNickname(finalNickname);
            user.updateProfileImage(finalProfileImage);
            user.updateRole(ERole.USER);

            User savedUser = userRepository.save(user);

            // 7. role 변경으로 인한 새로운 JWT 토큰 발급
            JwtTokenDto jwtTokenDto = jwtUtil.generateTokens(savedUser.getId(), savedUser.getRole());
            savedUser.updateRefreshToken(jwtTokenDto.getRefreshToken());
            userRepository.save(savedUser);

            log.info("회원가입 완료 - 사용자 ID: {}, 닉네임: {}, 권한: {}, 새 토큰 발급",
                    savedUser.getId(), savedUser.getNickname(), savedUser.getRole());

            return RegisterResponseDto.of(
                    savedUser.getId(),
                    savedUser.getRole().toString(),
                    savedUser.getNickname(),
                    s3PresignedUrlService.resolveProfileImageUrl(savedUser.getProfileImage()),
                    jwtTokenDto.getAccessToken(),
                    jwtTokenDto.getRefreshToken()
            );

        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("회원가입 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 프로필 이미지 업로드
     */
    private String uploadProfileImage(MultipartFile file) {
        // 이미지 파일 검증
        validateProfileImage(file);

        // 파일 키 생성 (profiles/{yyyy}/{MM}/{uuid}.{ext})
        String key = generateProfileImageKey(file.getOriginalFilename());

        // Object Storage에 업로드
        objectStorageService.upload(s3Properties.getBucket(), key, file);

        // S3 key 반환 (User.profileImage에 key 저장 — 응답 시 presigned 변환)
        log.info("프로필 이미지 업로드 완료 - key: {}", key);

        return key;
    }

    /**
     * 프로필 이미지 파일 검증
     */
    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE, "파일이 비어있습니다.");
        }

        // MIME 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new CommonException(ErrorCode.INVALID_FILE_FORMAT);
        }

        // 파일 크기 검증 (5MB)
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new CommonException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    /**
     * 프로필 이미지 키 생성
     */
    private String generateProfileImageKey(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        LocalDate now = LocalDate.now(KST);
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uuid = UUID.randomUUID().toString();

        // 키 규칙: profiles/{yyyy}/{MM}/{uuid}.{ext}
        return "profiles/" + datePath + "/" + uuid + extension;
    }

    /**
     * 액세스 토큰 갱신
     */
    @Transactional
    public SocialLoginResponseDto refreshAccessToken(String refreshToken) {
        try {
            // Refresh Token 유효성 검증
            if (!jwtUtil.validateToken(refreshToken)) {
                throw new CommonException(ErrorCode.INVALID_REFRESH_TOKEN);
            }

            // 사용자 조회
            User user = userRepository.findByRefreshToken(refreshToken)
                    .orElseThrow(() -> new CommonException(ErrorCode.INVALID_REFRESH_TOKEN));

            // 사용자 로그인 상태 확인
            if (!user.isLogin()) {
                throw new CommonException(ErrorCode.USER_NOT_LOGGED_IN);
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

            return SocialLoginResponseDto.of(
                    user.getId(),
                    user.getRole(),
                    newAccessToken,
                    newRefreshToken
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
    @Transactional
    public void logout(Long userId) {
        try {
            // 사용자 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

            user.logout();
            log.info("서비스 로그아웃 - 사용자 ID: {}", userId);

        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("로그아웃 처리 중 예상치 못한 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 회원 탈퇴
     * - Kakao: Admin Key로 unlink
     * - Apple: 저장된 refresh_token을 복호화 후 revoke
     * 외부 API는 트랜잭션 외부에서 수행, DB 반영은 WithdrawTransactionService.
     */
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        // 이미 탈퇴된 사용자 → 멱등 성공 처리
        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            log.info("이미 탈퇴된 사용자의 탈퇴 재요청 - 멱등 성공 처리: userId={}", userId);
            return;
        }

        switch (user.getEProvider()) {
            case KAKAO -> {
                long kakaoUserId = Long.parseLong(user.getSocialId());
                kakaoOAuth2Service.unlink(kakaoUserId);
            }
            case APPLE -> {
                String encrypted = user.getAppleRefreshToken();
                if (encrypted == null || encrypted.isBlank()) {
                    log.error("Apple 탈퇴 — apple_refresh_token 부재(설계상 발생 불가): userId={}", userId);
                    throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
                }
                String refreshToken = appleRefreshTokenEncryptor.decrypt(encrypted);
                appleOAuth2Service.revokeRefreshToken(refreshToken);
            }
        }

        withdrawTransactionService.finalizeWithdraw(userId);
        log.info("회원 탈퇴 완료 - userId: {}, provider: {}", userId, user.getEProvider());
    }

    /**
     * 사진 정보 활용 동의 처리 (멱등)
     * - 미동의 → 동의 처리 후 true 반환
     * - 기동의 → 추가 처리 없이 true 반환 (409 아님)
     */
    @Transactional
    public PhotoConsentResponse agreeToPhotoConsent(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasPhotoConsentAgreed()) {
            user.agreeToPhotoConsent();
        }

        return PhotoConsentResponse.of(user.hasPhotoConsentAgreed());
    }

    /**
     * 사진 정보 활용 동의 상태 조회
     */
    @Transactional(readOnly = true)
    public PhotoConsentResponse getPhotoConsentStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        return PhotoConsentResponse.of(user.hasPhotoConsentAgreed());
    }
}
