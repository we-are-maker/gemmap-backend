package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 토큰 생성 및 검증 유틸리티
 */
@Slf4j
@Component
public class JwtUtil implements InitializingBean {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    private SecretKey key;

    @Override
    public void afterPropertiesSet() {
        if (secretKey == null || secretKey.isEmpty()) {
            log.error("JWT 시크릿 키가 설정되지 않음");
            throw new CommonException(ErrorCode.JWT_SECRET_KEY_ERROR);
        }

        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            this.key = Keys.hmacShaKeyFor(keyBytes);

        } catch (IllegalArgumentException e) {
            log.error("JWT 시크릿 키 형식 오류: {}", e.getMessage());
            throw new CommonException(ErrorCode.JWT_SECRET_KEY_ERROR);
        }
    }

    public JwtTokenDto generateTokens(Long id, ERole eRole) {
        return JwtTokenDto.builder()
                .accessToken(generateAccessToken(id, eRole))
                .refreshToken(generateRefreshToken(id, eRole))
                .build();
    }

    public String generateAccessToken(Long id, ERole role) {
        return generateAccessToken(id, role, accessTokenExpirationMs);
    }

    public String generateAccessToken(Long id, ERole role, long expirationPeriod) {
        Claims claims = createUserClaims(id, role);
        return generateToken(claims, expirationPeriod);
    }

    public String generateRefreshToken(Long id, ERole role) {
        return generateRefreshToken(id, role, refreshTokenExpirationMs);
    }

    public String generateRefreshToken(Long id, ERole role, long expirationPeriod) {
        Claims claims = createUserClaims(id, role);
        return generateToken(claims, expirationPeriod);
    }

    public String generateToken(Claims claims, long expirationMillis) {
        JwtBuilder builder = Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key);

        if (claims != null) {
            builder.claims(claims);
        }
        return builder.compact();
    }

    private Claims createUserClaims(Long id, ERole role) {
        Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put(Constant.USER_ID_CLAIM_NAME, id.toString());
        claimsMap.put(Constant.USER_ROLE_CLAIM_NAME, role.toString());
        return Jwts.claims().add(claimsMap).build();
    }

    public Claims validateTokenAndGetClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            validateTokenAndGetClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public boolean isTokenExpiringSoon(String token, long thresholdMs) {
        try {
            Claims claims = validateTokenAndGetClaims(token);
            Date expiration = claims.getExpiration();
            long timeUntilExpiration = expiration.getTime() - System.currentTimeMillis();
            return timeUntilExpiration < thresholdMs;
        } catch (JwtException e) {
            return true;
        }
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpirationMs;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpirationMs;
    }
}