package com.gemmap.gemmap.auth.application.dto.apple;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Apple /auth/token 응답 DTO (authorization_code 교환 결과).
 *
 * Apple은 access_token, token_type, expires_in, refresh_token, id_token을 응답하지만,
 * 본 서비스에서 실제로 사용하는 값은 refresh_token 뿐이므로 나머지 필드는 역직렬화 시 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppleTokenResponse(
        @JsonProperty("refresh_token") String refreshToken
) {
}
