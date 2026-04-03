package com.gemmap.gemmap.auth.application.dto.request;

/**
 * Apple 로그인 요청 Body DTO
 * name은 최초 로그인 시 클라이언트가 전달해야 한다.
 */
public record AppleLoginRequestDto(
        String name
) {
}
