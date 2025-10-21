package com.gemmap.user.auth.application.dto.response;

/**
 * 카카오 토큰 갱신 응답 DTO
 */
public record KakaoTokenRefreshResponseDto(
        String kakaoAccessToken,    // 새로운 카카오 액세스 토큰
        String kakaoRefreshToken,   // 새로운 카카오 리프레시 토큰 (있을 경우)
        Integer expiresIn           // 토큰 만료 시간(초)
) {
    public static KakaoTokenRefreshResponseDto of(String accessToken, String refreshToken, Integer expiresIn) {
        return new KakaoTokenRefreshResponseDto(accessToken, refreshToken, expiresIn);
    }
}
