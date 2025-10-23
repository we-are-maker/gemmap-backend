package com.gemmap.common.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gemmap.common.dto.ExceptionDto;
import com.gemmap.common.exception.ErrorCode;
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
        result.put("success", false);
        result.put("data", null);
        result.put("error", new ExceptionDto(errorCode, errorCode.getMessage()));

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
