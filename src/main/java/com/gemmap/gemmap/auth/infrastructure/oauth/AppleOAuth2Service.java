package com.gemmap.gemmap.auth.infrastructure.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemmap.gemmap.auth.application.dto.apple.AppleIdentityTokenClaims;
import com.gemmap.gemmap.auth.application.dto.apple.AppleTokenResponse;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;

/**
 * Apple OAuth2 서비스
 * Apple Identity Token 검증, Authorization Code 교환, Token Revocation
 */
@Slf4j
@Service
public class AppleOAuth2Service {

    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String APPLE_REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final long CLIENT_SECRET_TTL_SECONDS = 300L; // 5분
    private static final String APPLE_REQUEST_USER_AGENT = "gemmap-backend";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${oauth2.apple.bundle-id}")
    private String bundleId;

    @Value("${oauth2.apple.team-id}")
    private String teamId;

    @Value("${oauth2.apple.key-id}")
    private String keyId;

    @Value("${oauth2.apple.private-key}")
    private String privateKeyPem;

    private ECPrivateKey cachedPrivateKey; // PEM 파싱 결과만 캐시 (JWT 자체는 매 요청 생성)

    public AppleOAuth2Service(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    // ========== Identity Token 검증 (기존) ==========

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
                    claims.getSubject()
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

    // ========== Authorization Code 교환 (신규) ==========

    /**
     * Apple authorization_code 교환 → refresh_token 확보
     */
    public AppleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.USER_AGENT, APPLE_REQUEST_USER_AGENT);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", bundleId);
        params.add("client_secret", generateClientSecret());
        params.add("code", authorizationCode);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<AppleTokenResponse> response = restTemplate.exchange(
                    APPLE_TOKEN_URL,
                    HttpMethod.POST,
                    request,
                    AppleTokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null
                    && response.getBody().refreshToken() != null) {
                return response.getBody();
            }

            log.error("Apple token exchange 비정상 응답: status={}", response.getStatusCode());
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        } catch (HttpClientErrorException.BadRequest e) {
            // authorization code 재사용/만료(invalid_grant) 등 클라이언트 요인 400 에러
            log.warn("Apple token exchange 400: body={}", e.getResponseBodyAsString());
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        } catch (CommonException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Apple token exchange RestClient 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        }
    }

    // ========== Token Revocation (신규) ==========

    /**
     * Apple refresh_token revoke.
     * 성공(200) 또는 invalid_grant(400) → 멱등 성공 처리.
     */
    public void revokeRefreshToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.USER_AGENT, APPLE_REQUEST_USER_AGENT);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", bundleId);
        params.add("client_secret", generateClientSecret());
        params.add("token", refreshToken);
        params.add("token_type_hint", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    APPLE_REVOKE_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Apple token revoke 성공");
                return;
            }

            log.error("Apple revoke 비정상 응답: status={}, body={}", response.getStatusCode(), response.getBody());
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        } catch (HttpClientErrorException.BadRequest e) {
            // invalid_grant: 이미 무효화된 토큰 → 멱등 성공
            String body = e.getResponseBodyAsString();
            if (body != null && body.contains("invalid_grant")) {
                log.info("Apple revoke: 이미 무효화된 토큰 (invalid_grant) - 멱등 성공 처리");
                return;
            }
            log.error("Apple revoke 400: body={}", body);
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        } catch (CommonException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Apple revoke RestClient 오류: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.OAUTH2_COMMUNICATION_ERROR);
        }
    }

    // ========== client_secret JWT 생성 (Apple 공식 권장: 매 요청 생성) ==========

    private String generateClientSecret() {
        try {
            ECPrivateKey privateKey = loadPrivateKey();
            Instant now = Instant.now();

            return Jwts.builder()
                    .header()
                        .keyId(keyId)
                        .and()
                    .issuer(teamId)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusSeconds(CLIENT_SECRET_TTL_SECONDS)))
                    .audience().add(APPLE_ISSUER).and()
                    .subject(bundleId)
                    .signWith(privateKey, Jwts.SIG.ES256)
                    .compact();
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple client_secret 생성 실패: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * .p8 PEM → ECPrivateKey (BouncyCastle 없이 JCA 표준)
     */
    private synchronized ECPrivateKey loadPrivateKey() {
        if (cachedPrivateKey != null) {
            return cachedPrivateKey;
        }
        try {
            String pem = privateKeyPem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            cachedPrivateKey = (ECPrivateKey) keyFactory.generatePrivate(spec);
            return cachedPrivateKey;
        } catch (Exception e) {
            log.error("Apple .p8 Private Key 로딩 실패: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    // ========== Identity Token 내부 메서드 (기존 유지) ==========

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
