package com.gemmap.gemmap.auth.application.service;

import com.gemmap.gemmap.auth.application.dto.kakao.KakaoAccessTokenInfoResponse;
import com.gemmap.gemmap.auth.application.dto.kakao.KakaoUserInfoResponse;
import com.gemmap.gemmap.auth.application.dto.response.KakaoLoginResponseDto;
import com.gemmap.gemmap.auth.application.dto.response.PhotoConsentResponse;
import com.gemmap.gemmap.auth.application.dto.response.RegisterResponseDto;
import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.auth.domain.repository.UserRepository;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtTokenDto;
import com.gemmap.gemmap.auth.infrastructure.jwt.JwtUtil;
import com.gemmap.gemmap.auth.infrastructure.oauth.KakaoOAuth2Service;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.ObjectStorageService;
import com.gemmap.gemmap.shared.infrastructure.objectstorage.S3UrlGenerator;
import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.EProvider;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.config.s3.S3Properties;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * - 소셜 로그인 (Kakao)
 * - 회원가입 (GUEST → USER 권한 전환)
 * - 토큰 갱신 (Access Token + Refresh Token Rotation)
 * - 로그아웃
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final KakaoOAuth2Service kakaoOAuth2Service;
    private final ObjectStorageService objectStorageService;
    private final S3UrlGenerator s3UrlGenerator;
    private final S3Properties s3Properties;

    /**
     * 카카오 Access Token으로 인증 (모바일 SDK 방식)
     * 모바일 앱에서 획득한 카카오 Access Token을 검증하고 서비스 JWT 발급
     */
    @Transactional
    public KakaoLoginResponseDto authenticateWithKakaoAccessToken(String kakaoAccessToken) {
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

            return KakaoLoginResponseDto.of(
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
                User savedUser = userRepository.save(existingUser);
                log.info("기존 카카오 사용자 정보 업데이트 - 사용자 ID: {}", savedUser.getId());
                return savedUser;
            }

            // 신규 사용자 생성
            User newUser = createKakaoUser(socialId, email, userInfo);
            User savedUser = userRepository.save(newUser);
            log.info("신규 카카오 사용자 생성 - 소셜 ID: {}, 사용자 ID: {}", socialId, savedUser.getId());
            return savedUser;
        } catch (Exception e) {
            log.error("카카오 사용자 처리 중 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.DATABASE_ERROR);
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
                    savedUser.getProfileImage(),
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

        // URL 생성 및 반환
        String fileUrl = s3UrlGenerator.generateUrl(key);
        log.info("프로필 이미지 업로드 완료 - URL: {}", fileUrl);

        return fileUrl;
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
    public KakaoLoginResponseDto refreshAccessToken(String refreshToken) {
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

            return KakaoLoginResponseDto.of(
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