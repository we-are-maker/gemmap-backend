package com.gemmap.gemmap.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemmap.gemmap.auth.application.dto.apple.AppleIdentityTokenClaims;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Collection;

/**
 * Apple OAuth2 서비스
 * Apple Identity Token 검증 및 Apple 공개키 조회
 */
@Slf4j
@Service
public class AppleOAuth2Service {

    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${oauth2.apple.bundle-id}")
    private String bundleId;

    public AppleOAuth2Service(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public AppleIdentityTokenClaims validateAndExtractClaims(String identityToken) {
        try {
            String kid = extractKidFromToken(identityToken);
            PublicKey publicKey = fetchPublicKey(kid);

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(identityToken)
                    .getPayload();

            validateClaims(claims);

            return new AppleIdentityTokenClaims(
                    claims.getSubject(),
                    claims.get("email", String.class)
            );
        } catch (CommonException e) {
            throw e;
        } catch (JwtException e) {
            log.error("Apple Identity Token JWT 검증 실패: {}", e.getMessage());
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        } catch (Exception e) {
            log.error("Apple Identity Token 처리 중 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }
    }

    private String extractKidFromToken(String identityToken) {
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new CommonException(ErrorCode.INVALID_TOKEN);
            }

            String decodedHeader = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode headerNode = objectMapper.readTree(decodedHeader);
            String kid = headerNode.path("kid").asText(null);

            if (kid == null || kid.isEmpty()) {
                log.error("Apple Identity Token 헤더에 kid 없음");
                throw new CommonException(ErrorCode.INVALID_TOKEN);
            }

            return kid;
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple Identity Token 헤더 파싱 실패: {}", e.getMessage());
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }
    }

    private PublicKey fetchPublicKey(String kid) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(APPLE_JWKS_URL, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                log.error("Apple JWKS 조회 실패: {}", response.getStatusCode());
                throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
            }

            JsonNode jwks = objectMapper.readTree(response.getBody());
            JsonNode keys = jwks.get("keys");
            if (keys == null || !keys.isArray()) {
                throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
            }

            for (JsonNode key : keys) {
                if (kid.equals(key.path("kid").asText())) {
                    return convertJwkToPublicKey(key);
                }
            }

            log.error("Apple JWKS에서 kid {} 를 찾을 수 없음", kid);
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple 공개키 조회 중 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        }
    }

    private PublicKey convertJwkToPublicKey(JsonNode jwk) {
        try {
            byte[] nBytes = Base64.getUrlDecoder().decode(jwk.get("n").asText());
            byte[] eBytes = Base64.getUrlDecoder().decode(jwk.get("e").asText());

            BigInteger n = new BigInteger(1, nBytes);
            BigInteger e = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("JWK → RSA PublicKey 변환 실패: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        }
    }

    private void validateClaims(Claims claims) {
        if (!APPLE_ISSUER.equals(claims.getIssuer())) {
            log.error("Apple Identity Token iss 불일치 - 예상: {}, 실제: {}", APPLE_ISSUER, claims.getIssuer());
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }

        Object audienceClaim = claims.get("aud");
        if (!matchesAudience(audienceClaim)) {
            log.error("Apple Identity Token aud 불일치 - 예상: {}, 실제: {}", bundleId, audienceClaim);
            throw new CommonException(ErrorCode.INVALID_TOKEN);
        }
    }

    private boolean matchesAudience(Object audienceClaim) {
        if (audienceClaim instanceof String aud) {
            return bundleId.equals(aud);
        }

        if (audienceClaim instanceof Collection<?> audCollection) {
            return audCollection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .anyMatch(bundleId::equals);
        }

        return false;
    }
}
