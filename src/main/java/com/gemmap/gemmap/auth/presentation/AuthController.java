package com.gemmap.gemmap.auth.presentation;

import com.gemmap.gemmap.auth.application.dto.response.KakaoLoginResponseDto;
import com.gemmap.gemmap.auth.application.dto.response.RegisterResponseDto;
import com.gemmap.gemmap.auth.application.service.AuthService;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.dto.ResponseDto;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.shared.util.HeaderUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
     * 카카오 로그인 시작 (리다이렉트 방식)
     */
    @PostMapping("/login/kakao")
    public void startKakaoLogin(HttpServletResponse response) throws IOException {
        log.info("카카오 로그인 시작 요청");
        String authUrl = authService.getKakaoAuthorizationUrl();
        // 카카오 로그인 페이지로 리다이렉트
        response.sendRedirect(authUrl);
    }

    /**
     * 카카오 로그인 콜백 처리 (브라우저 리다이렉트)
     */
    @GetMapping("/kakao/callback")
    public ResponseDto<KakaoLoginResponseDto> kakaoCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state) {
        KakaoLoginResponseDto loginResponse = authService.kakaoLogin(code);
        log.info("카카오 로그인 성공 - 사용자 ID: {}", loginResponse.userId());
        return ResponseDto.ok(loginResponse);
    }

    /**
     * 카카오 로그인 (모바일 SDK 방식)
     * 모바일 앱에서 획득한 카카오 Access Token으로 인증
     */
    @PostMapping("/kakao/login")
    public ResponseDto<KakaoLoginResponseDto> kakaoSdkLogin(HttpServletRequest request) {
        log.info("카카오 SDK 로그인 요청");

        // Authorization 헤더에서 카카오 Access Token 추출
        String kakaoAccessToken = HeaderUtil.refineHeader(request, Constant.AUTHORIZATION_HEADER, Constant.BEARER_PREFIX)
                .orElseThrow(() -> new CommonException(ErrorCode.INVALID_TOKEN));

        KakaoLoginResponseDto loginResponse = authService.authenticateWithKakaoAccessToken(kakaoAccessToken);
        log.info("카카오 SDK 로그인 성공 - 사용자 ID: {}", loginResponse.userId());
        return ResponseDto.ok(loginResponse);
    }

    /**
     * 서비스 토큰 갱신
     * 서비스 JWT 액세스 토큰 갱신
     */
    @PostMapping("/refresh")
    public ResponseDto<KakaoLoginResponseDto> refreshToken(@UserId Long userId) {
        log.info("서비스 토큰 갱신 요청 - 사용자 ID: {}", userId);
        KakaoLoginResponseDto loginResponse = authService.refreshAccessTokenWithUserInfo(userId);
        return ResponseDto.ok(loginResponse);
    }

    /**
     * 서비스 로그아웃
     * 클라이언트에서 토큰을 삭제하도록 하는 로그아웃
     */
    @PostMapping("/logout")
    public ResponseDto<?> logout(@UserId Long userId) {
        log.info("서비스 로그아웃 요청 - 사용자 ID: {}", userId);
        authService.simpleLogout(userId);
        return ResponseDto.noContent();
    }

    /**
     * 회원가입 (GUEST → USER 권한 전환)
     * 닉네임과 프로필 이미지를 업데이트하고 권한을 USER로 변경
     */
    @PostMapping("/register")
    public ResponseDto<RegisterResponseDto> register(
            @UserId Long userId,
            @RequestParam(value = "nickname", required = false) String nickname,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage) {
        log.info("회원가입 요청 - 사용자 ID: {}, 닉네임 입력 여부: {}, 프로필 이미지 업로드 여부: {}",
                userId, nickname != null, profileImage != null && !profileImage.isEmpty());

        RegisterResponseDto response = authService.register(userId, nickname, profileImage);
        return ResponseDto.ok(response);
    }
}