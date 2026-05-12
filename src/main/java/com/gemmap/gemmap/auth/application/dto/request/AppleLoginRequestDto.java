package com.gemmap.gemmap.auth.application.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Apple 로그인 요청 Body DTO.
 * - name, email: 최초 로그인 시 클라이언트가 전달
 * - authorizationCode: Apple revoke 호출용 refresh_token 확보에 필수
 */
public record AppleLoginRequestDto(
        String name,
        String email,
        @NotBlank(message = "authorizationCode는 필수입니다.")
        String authorizationCode
) {
}
