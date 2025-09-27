package com.gemmap.gemmap.shared.common.dto;

import com.gemmap.gemmap.shared.exception.CommonException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpStatus;

/**
 * 표준화된 API 응답 DTO
 * 모든 API 응답의 일관된 형식 제공 (data + error 구조)
 *
 * - 성공 응답 생성 (200 OK, 201 Created, 204 No Content)
 * - 실패 응답 생성 (GlobalExceptionHandler에서 사용)
 * - 제네릭을 통한 타입 안전성 보장
 * - HTTP 상태 코드와 응답 데이터의 일관된 구조 제공
 */
public record ResponseDto<T>(@JsonIgnore HttpStatus httpStatus,
                             @Nullable T data,
                             @Nullable ExceptionDto error) {

    /**
     * 200 OK 성공 응답 생성
     */
    public static <T> ResponseDto<T> ok(@Nullable final T data) {
        return new ResponseDto<>(HttpStatus.OK, data, null);
    }

    /**
     * 201 Created 생성 성공 응답 생성
     */
    public static <T> ResponseDto<T> created(@Nullable final T data) {
        return new ResponseDto<>(HttpStatus.CREATED, data, null);
    }

    /**
     * 204 No Content 성공 응답 생성
     */
    public static <T> ResponseDto<T> noContent() {
        return new ResponseDto<>(HttpStatus.NO_CONTENT, null, null);
    }

    /**
     * 실패 응답 생성 (GlobalExceptionHandler에서 사용)
     */
    public static <T> ResponseDto<T> fail(final CommonException e) {
        return new ResponseDto<>(e.getErrorCode().getHttpStatus(), null,
                new ExceptionDto(e.getErrorCode(), e.getMessage()));
    }
}