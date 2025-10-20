package com.gemmap.common.dto;

import com.gemmap.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 예외 정보 전송 DTO
 */
@Getter
public class ExceptionDto {

    private int code;
    private String message;

    public ExceptionDto(ErrorCode errorCode, String message) {
        this.code = errorCode.getCode();
        this.message = message;
    }
}
