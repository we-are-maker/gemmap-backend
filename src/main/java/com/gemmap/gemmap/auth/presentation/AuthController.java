package com.gemmap.gemmap.auth.presentation;

import com.gemmap.gemmap.auth.application.dto.request.AppleLoginRequestDto;
import com.gemmap.gemmap.auth.application.dto.request.PhotoConsentRequest;
import com.gemmap.gemmap.auth.application.dto.response.PhotoConsentResponse;
import com.gemmap.gemmap.auth.application.dto.response.RegisterProfileResponseDto;
import com.gemmap.gemmap.auth.application.dto.response.SocialLoginResponseDto;
import com.gemmap.gemmap.shared.common.enums.EGender;
import org.springframework.format.annotation.DateTimeFormat;
import com.gemmap.gemmap.auth.application.service.AuthService;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.util.HeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 인증 관련 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 카카오 로그인 (모바일 SDK 방식)
     * 모바일 앱에서 획득한 카카오 Access Token으로 인증
     */
    @PostMapping("/kakao/login")
    public ResponseEntity<SocialLoginResponseDto> kakaoSdkLogin(HttpServletRequest request) {
        log.info("카카오 SDK 로그인 요청");

        // Authorization 헤더에서 카카오 Access Token 추출
        String kakaoAccessToken = HeaderUtil.refineHeader(request, Constant.AUTHORIZATION_HEADER, Constant.BEARER_PREFIX)
                .orElseThrow(() -> new CommonException(ErrorCode.INVALID_TOKEN));

        SocialLoginResponseDto loginResponse = authService.authenticateWithKakaoAccessToken(kakaoAccessToken);
        log.info("카카오 SDK 로그인 성공 - 사용자 ID: {}", loginResponse.userId());
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * Apple 로그인 (모바일 SDK 방식)
     * 모바일 앱에서 획득한 Apple Identity Token으로 인증
     */
    @PostMapping("/apple/login")
    public ResponseEntity<SocialLoginResponseDto> appleLogin(
            HttpServletRequest request,
            @Valid @RequestBody AppleLoginRequestDto loginRequest) {
        log.info("Apple 로그인 요청");

        String identityToken = HeaderUtil.refineHeader(request, Constant.AUTHORIZATION_HEADER, Constant.BEARER_PREFIX)
                .orElseThrow(() -> new CommonException(ErrorCode.INVALID_TOKEN));

        SocialLoginResponseDto loginResponse = authService.authenticateWithAppleToken(
                identityToken,
                loginRequest.email(),
                loginRequest.name(),
                loginRequest.authorizationCode()
        );
        log.info("Apple 로그인 성공 - 사용자 ID: {}", loginResponse.userId());
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * 회원가입 (GUEST → USER 권한 전환)
     * 닉네임과 프로필 이미지를 업데이트하고 권한을 USER로 변경
     */
    @PostMapping("/register")
    public ResponseEntity<SocialLoginResponseDto> register(
            @UserId Long userId,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "birthDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate,
            @RequestParam(value = "gender", required = false) EGender gender,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage) {
        log.info("회원가입 요청 - 사용자 ID: {}, 닉네임 입력: {}, 생일 입력: {}, 성별 입력: {}, 이미지 업로드: {}",
                userId, nickname != null, birthDate != null, gender != null,
                profileImage != null && !profileImage.isEmpty());

        SocialLoginResponseDto response = authService.register(userId, nickname, birthDate, gender, profileImage);
        return ResponseEntity.ok(response);
    }

    /**
     * 회원가입 화면 prefill 조회 (GUEST 토큰)
     */
    @GetMapping("/register-profile")
    public ResponseEntity<RegisterProfileResponseDto> getRegisterProfile(@UserId Long userId) {
        log.info("회원가입 프리필 조회 요청 - 사용자 ID: {}", userId);
        return ResponseEntity.ok(authService.getRegisterProfile(userId));
    }

    /**
     * 서비스 토큰 갱신
     * 서비스 JWT 액세스 토큰 갱신
     */
    @PostMapping("/refresh")
    public ResponseEntity<SocialLoginResponseDto> refreshToken(HttpServletRequest request) {
        log.info("서비스 토큰 갱신 요청");

        // Authorization 헤더에서 서비스 Refresh Token 추출
        String refreshToken = HeaderUtil.refineHeader(request, Constant.AUTHORIZATION_HEADER, Constant.BEARER_PREFIX)
                .orElseThrow(() -> new CommonException(ErrorCode.INVALID_TOKEN));

        SocialLoginResponseDto loginResponse = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(loginResponse);
    }

    /**
     * 서비스 로그아웃
     * 리프레시 토큰을 삭제하도록 하는 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@UserId Long userId) {
        log.info("서비스 로그아웃 요청 - 사용자 ID: {}", userId);

        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 회원 탈퇴
     * - Provider(Kakao/Apple)별 연결 해제 수행 후 서비스 탈퇴 처리
     */
    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@UserId Long userId) {
        log.info("회원 탈퇴 요청 - 사용자 ID: {}", userId);
        authService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 사진 정보 활용 동의/철회 처리 (양방향 멱등)
     * POST /api/v1/auth/photo-consent
     * Body: { "agreed": boolean }  // 필수
     */
    @PostMapping("/photo-consent")
    public ResponseEntity<PhotoConsentResponse> setPhotoConsent(
            @UserId Long userId,
            @Valid @RequestBody PhotoConsentRequest request
    ) {
        return ResponseEntity.ok(authService.setPhotoConsent(userId, request.agreed()));
    }

    /**
     * 사진 정보 활용 동의 상태 조회
     * GET /api/v1/auth/photo-consent
     */
    @GetMapping("/photo-consent")
    public ResponseEntity<PhotoConsentResponse> getPhotoConsentStatus(@UserId Long userId) {
        return ResponseEntity.ok(authService.getPhotoConsentStatus(userId));
    }
}
