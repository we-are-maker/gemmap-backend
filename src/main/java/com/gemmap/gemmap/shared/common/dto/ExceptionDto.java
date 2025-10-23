package com.gemmap.gemmap.shared.common.dto;

import com.gemmap.gemmap.shared.exception.ErrorCode;
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