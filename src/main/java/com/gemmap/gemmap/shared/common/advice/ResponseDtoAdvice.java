package com.gemmap.gemmap.shared.common.advice;

import com.gemmap.gemmap.shared.common.dto.ResponseDto;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * ResponseDto의 httpStatus를 실제 HTTP 응답 상태 코드로 자동 설정
 *
 * - ResponseDto를 반환하는 모든 컨트롤러에 자동 적용
 * - httpStatus 필드를 읽어서 HTTP 응답의 상태 코드로 설정
 * - 기존 컨트롤러 코드 변경 없이 전역적으로 동작
 */
@RestControllerAdvice
public class ResponseDtoAdvice implements ResponseBodyAdvice<ResponseDto<?>> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // ResponseDto 타입을 반환하는 메서드에만 적용
        return ResponseDto.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public ResponseDto<?> beforeBodyWrite(
            ResponseDto<?> body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body != null && body.httpStatus() != null) {
            // ResponseDto의 httpStatus를 HTTP 응답 상태 코드로 설정
            response.setStatusCode(body.httpStatus());
        }
        return body;
    }
}
