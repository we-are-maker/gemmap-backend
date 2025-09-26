package com.gemmap.gemmap.shared.exception;

import com.gemmap.gemmap.shared.common.dto.ResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/**
 * 전역 예외 처리 핸들러
 *
 * - 애플리케이션에서 발생하는 모든 예외를 표준화된 ResponseDto 응답으로 변환
 * - JWT 토큰 관련 예외 처리
 * - Spring Security 인증/권한 예외 처리
 * - HTTP 요청 관련 예외 처리
 * - 비즈니스 로직 예외 처리 (CommonException)
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ========== JWT 및 인증 관련 예외 처리 ==========

    /**
     * JWT 토큰 관련 예외 처리
     */
    @ExceptionHandler(JwtException.class)
    public ResponseDto<?> handleJwtException(JwtException e) {
        log.error("JWT 토큰 예외 발생: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.INVALID_TOKEN));
    }

    /**
     * 인증 실패 처리
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseDto<?> handleAuthenticationException(Exception e) {
        log.error("인증 실패: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 권한 부족 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseDto<?> handleAccessDeniedException(AccessDeniedException e) {
        log.error("권한 부족: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.ACCESS_DENIED));
    }

    // ========== HTTP 요청 관련 예외 처리 ==========

    /**
     * 지원하지 않는 Content-Type 요청 처리
     */
    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, MultipartException.class})
    public ResponseDto<?> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.error("미디어 타입 오류: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    /**
     * 존재하지 않는 URI 요청 처리 (404 Not Found)
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseDto<?> handleNoHandlerFoundException(NoHandlerFoundException e) {
        log.error("핸들러 없음: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * JSON 파싱 실패 등 HTTP 메시지 바디 읽기 오류
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseDto<?> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("메시지 읽기 오류: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.INVALID_INPUT_VALUE));
    }

    /**
     * @Valid 유효성 검사 실패 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseDto<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("유효성 검사 실패: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * 지원하지 않는 HTTP 메서드 요청 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseDto<?> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.error("지원하지 않는 메서드: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 요청 파라미터 타입 불일치 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseDto<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("파라미터 타입 오류: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.TYPE_MISMATCH));
    }

    /**
     * 필수 요청 파라미터 누락 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseDto<?> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        log.error("필수 파라미터 누락: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.MISSING_REQUEST_PARAMETER));
    }

    // ========== 데이터베이스 및 비즈니스 예외 처리 ==========

    /**
     * 데이터 무결성 위반 처리 (중복 키 등)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseDto<?> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("데이터 무결성 오류: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.DUPLICATE_RESOURCE));
    }

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(CommonException.class)
    public ResponseDto<?> handleApiException(CommonException e) {
        log.error("비즈니스 예외: {}", e.getMessage());
        return ResponseDto.fail(e);
    }

    /**
     * 기타 모든 예외 처리 (최종 catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseDto<?> handleException(Exception e) {
        log.error("시스템 오류: {}", e.getMessage());
        return ResponseDto.fail(new CommonException(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}