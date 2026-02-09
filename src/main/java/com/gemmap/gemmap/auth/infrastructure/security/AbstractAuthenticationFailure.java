package com.gemmap.gemmap.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemmap.gemmap.shared.common.dto.ExceptionDto;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractAuthenticationFailure {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected void setErrorResponse(
            HttpServletResponse response,
            ErrorCode errorCode) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(errorCode.getHttpStatus().value());

        Map<String, Object> result = new HashMap<>();
        result.put("error", new ExceptionDto(errorCode, errorCode.getMessage()));

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}