package com.gemmap.gemmap.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 정의
 */
@Getter
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(40000, HttpStatus.BAD_REQUEST, "잘못된 입력 값입니다."),
    MISSING_REQUEST_PARAMETER(40001, HttpStatus.BAD_REQUEST, "요청 파라미터가 누락되었습니다."),
    METHOD_NOT_ALLOWED(40002, HttpStatus.METHOD_NOT_ALLOWED, "허용되지 않은 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(40003, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
    DUPLICATE_RESOURCE(40004, HttpStatus.BAD_REQUEST, "이미 존재하는 리소스입니다."),
    VALIDATION_ERROR(40005, HttpStatus.BAD_REQUEST, "유효성 검사에 실패했습니다."),
    TYPE_MISMATCH(40006, HttpStatus.BAD_REQUEST, "요청 파라미터 타입이 올바르지 않습니다."),
    INVALID_PROVIDER(40007, HttpStatus.BAD_REQUEST, "지원하지 않는 OAuth2 제공자입니다."),

    // 401 Unauthorized
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_TOKEN(40101, HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(40102, HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(40103, HttpStatus.UNAUTHORIZED, "만료된 Refresh Token입니다."),
    INVALID_REFRESH_TOKEN(40104, HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    TOKEN_MALFORMED(40105, HttpStatus.UNAUTHORIZED, "토큰 형식이 올바르지 않습니다."),
    TOKEN_UNSUPPORTED(40106, HttpStatus.UNAUTHORIZED, "지원하지 않는 토큰입니다."),
    TOKEN_UNKNOWN(40107, HttpStatus.UNAUTHORIZED, "알 수 없는 JWT 오류입니다."),
    LOGIN_FAILED(40108, HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    USER_NOT_LOGGED_IN(40109, HttpStatus.UNAUTHORIZED, "로그인되지 않은 사용자입니다."),

    // 403 Forbidden
    ACCESS_DENIED(40300, HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_ROLE(40301, HttpStatus.FORBIDDEN, "요청한 리소스에 접근할 수 있는 권한이 없습니다."),

    // 404 Not Found
    RESOURCE_NOT_FOUND(40400, HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),

    // 409 Conflict
    CONFLICT(40900, HttpStatus.CONFLICT, "요청이 현재 리소스 상태와 충돌합니다."),
    ALREADY_EXISTS(40901, HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
    ALREADY_REGISTERED_USER(40902, HttpStatus.CONFLICT, "이미 회원가입이 완료된 사용자입니다."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),
    TOKEN_GENERATION_FAILED(50001, HttpStatus.INTERNAL_SERVER_ERROR, "토큰 생성에 실패했습니다."),
    DATABASE_ERROR(50002, HttpStatus.INTERNAL_SERVER_ERROR, "데이터베이스 처리 중 오류가 발생했습니다."),
    EXTERNAL_SERVICE_ERROR(50003, HttpStatus.INTERNAL_SERVER_ERROR, "외부 서비스 연동 중 오류가 발생했습니다."),
    JWT_SECRET_KEY_ERROR(50004, HttpStatus.INTERNAL_SERVER_ERROR, "JWT 시크릿 키 설정 오류입니다."),

    // 파일 및 스토리지 관련 에러
    IMAGE_UPLOAD_FAILED(50010, HttpStatus.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다."),
    S3_OPERATION_FAILED(50011, HttpStatus.INTERNAL_SERVER_ERROR, "Object Storage 작업 중 오류가 발생했습니다."),
    IMAGE_DELETE_FAILED(50012, HttpStatus.INTERNAL_SERVER_ERROR, "이미지 삭제에 실패했습니다."),
    INVALID_FILE_FORMAT(40010, HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(40011, HttpStatus.BAD_REQUEST, "파일 크기가 너무 큽니다."),

    // OAuth2 관련 에러 (502 Bad Gateway - 외부 서비스 오류)
    KAKAO_TOKEN_REQUEST_FAILED(50201, HttpStatus.BAD_GATEWAY, "카카오 토큰 요청에 실패했습니다."),
    KAKAO_USER_INFO_REQUEST_FAILED(50202, HttpStatus.BAD_GATEWAY, "카카오 사용자 정보 조회에 실패했습니다."),
    KAKAO_TOKEN_REFRESH_FAILED(50203, HttpStatus.BAD_GATEWAY, "카카오 토큰 갱신에 실패했습니다."),
    OAUTH2_COMMUNICATION_ERROR(50204, HttpStatus.BAD_GATEWAY, "OAuth2 제공자와의 통신 중 오류가 발생했습니다.");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}