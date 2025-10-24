package com.gemmap.gemmap.shared.common.constants;

/**
 * 전역 상수 정의
 */
public class Constant {

    // JWT Claims 키
    public static final String USER_ID_CLAIM_NAME = "userId";
    public static final String USER_ROLE_CLAIM_NAME = "role";

    // JWT 헤더 관련
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTHORIZATION_HEADER = "Authorization";

    // 토큰 순환 임계값 (Refresh Token 유효 기간의 50%)
    public static final double REFRESH_TOKEN_ROTATION_THRESHOLD = 0.5;

    // 사용자 기본 프로필 이미지
    public static final String DEFAULT_PROFILE_IMAGE = "default.png";

    // 인증이 필요 없는 경로
    public static final String[] NO_NEED_AUTH_URLS = {
            "/api/v1/auth/login/kakao",
            "/api/v1/auth/kakao/callback",
            "/api/v1/auth/kakao/login",
//            "/api/v1/auth/register",
            "/api/v1/test",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/configuration/**",
            "/favicon.ico",
            "/actuator/**",
            "/guest/**"
    };

    // 인증된 일반 사용자가 접근 가능한 경로
    public static final String[] USER_URLS = {
            "/api/v1/users/**"
    };

    // 관리자만 접근 가능한 경로
    public static final String[] ADMIN_URLS = {
            "/api/v1/admin/**"
    };
}