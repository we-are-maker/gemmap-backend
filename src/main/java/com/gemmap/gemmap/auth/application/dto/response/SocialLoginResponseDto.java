package com.gemmap.gemmap.auth.application.dto.response;

import com.gemmap.gemmap.shared.common.enums.ERole;

/**
 * 소셜 로그인 응답 DTO
 */
public record SocialLoginResponseDto(
        Long userId,                // 사용자 ID
        String role,                // 사용자 역할
        String accessToken,         // 서비스 JWT 액세스 토큰
        String refreshToken         // 서비스 JWT 리프레시 토큰
) {
    public static SocialLoginResponseDto of(Long userId, ERole role, String accessToken, String refreshToken) {
        return new SocialLoginResponseDto(userId, role.toString(), accessToken, refreshToken);
    }
}
