package com.gemmap.gemmap.auth.application.dto.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카카오 액세스 토큰 정보 응답 DTO
 * GET /v1/user/access_token_info 응답
 */
@Getter
@NoArgsConstructor
public class KakaoAccessTokenInfoResponse {

    @JsonProperty("app_id")
    private Long appId;

    @JsonProperty("id")
    private Long id;

    @JsonProperty("expires_in")
    private Integer expiresIn;
}
