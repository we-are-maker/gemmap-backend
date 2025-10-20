package com.gemmap.common.security.jwt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * JWT 토큰 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtTokenDto {
    private String accessToken;
    private String refreshToken;
}
