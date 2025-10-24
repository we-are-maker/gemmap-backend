package com.gemmap.gemmap.auth.application.dto.response;

import com.gemmap.gemmap.shared.common.enums.ERole;

/**
 * 카카오 로그인 응답 DTO (단순화된 버전)
 */
public record KakaoLoginResponseDto(
        Long userId,                // 사용자 ID
        String role,                // 사용자 역할
        String accessToken,         // 서비스 JWT 액세스 토큰
        String refreshToken         // 서비스 JWT 리프레시 토큰
) {
    public static KakaoLoginResponseDto of(Long userId, ERole role, String accessToken, String refreshToken) {
        return new KakaoLoginResponseDto(userId, role.toString(), accessToken, refreshToken);
    }
}