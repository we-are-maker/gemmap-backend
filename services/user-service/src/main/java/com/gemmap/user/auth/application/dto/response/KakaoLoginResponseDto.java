package com.gemmap.user.auth.application.dto.response;

import com.gemmap.common.enums.ERole;

/**
 * 카카오 로그인 응답 DTO (단순화된 버전)
 */
public record KakaoLoginResponseDto(
        String accessToken,         // 서비스 JWT 액세스 토큰
        String refreshToken,        // 서비스 JWT 리프레시 토큰
        String role,                // 사용자 역할
        Long userId                 // 사용자 ID
) {
    public static KakaoLoginResponseDto of(String accessToken, String refreshToken, ERole role, Long userId) {
        return new KakaoLoginResponseDto(accessToken, refreshToken, role.toString(), userId);
    }
}
