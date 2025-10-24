package com.gemmap.gemmap.auth.application.dto.response;

/**
 * 회원가입 완료 응답 DTO
 * role 변경으로 인해 새로운 JWT 토큰도 함께 반환
 */
public record RegisterResponseDto(
        Long userId,
        String role,
        String nickname,
        String profileImage,
        String accessToken,
        String refreshToken
) {
    public static RegisterResponseDto of(Long userId, String role, String nickname, String profileImage,
                                          String accessToken, String refreshToken) {
        return new RegisterResponseDto(userId, role, nickname, profileImage, accessToken, refreshToken);
    }
}
