package com.gemmap.gemmap.shared.common.enums;

/**
 * 인증 제공자 열거형
 * 사용자의 로그인 방식(소셜 로그인 제공자) 구분
 *
 * - Kakao OAuth2 소셜 로그인 지원
 * - 사용자 계정과 소셜 로그인 제공자 연결
 * - 향후 추가 소셜 로그인 제공자 확장 가능
 */
public enum EProvider {
    KAKAO,   // 카카오 소셜 로그인
    APPLE    // 애플 소셜 로그인
}
