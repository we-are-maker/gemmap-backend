package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.shared.exception.ErrorCode;
import com.gemmap.gemmap.auth.infrastructure.security.AbstractAuthenticationFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JWT 접근 거부 처리 핸들러 (403 Forbidden)
 */
@Component
public class JwtAccessDeniedHandler extends AbstractAuthenticationFailure implements AccessDeniedHandler {

    @Override
    public void handle(final HttpServletRequest request,
                       final HttpServletResponse response,
                       final AccessDeniedException accessDeniedException) throws IOException {
        setErrorResponse(response, ErrorCode.ACCESS_DENIED);
    }
}