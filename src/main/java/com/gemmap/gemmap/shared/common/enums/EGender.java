package com.gemmap.gemmap.shared.common.enums;

import lombok.extern.slf4j.Slf4j;

/**
 * 성별 열거형.
 * 소셜 제공자 원본 문자열(카카오 male/female)을 서비스 표준값으로 정규화한다.
 */
@Slf4j
public enum EGender {
    MALE,
    FEMALE;

    /**
     * 카카오 gender 문자열(male/female)을 EGender로 변환.
     * - "male"   -> MALE
     * - "female" -> FEMALE
     * - null / 그 외 값 -> null (+ warn 로그). 로그인 자체는 실패시키지 않는다.
     */
    public static EGender fromKakao(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw.trim().toLowerCase()) {
            case "male":
                return MALE;
            case "female":
                return FEMALE;
            default:
                log.warn("알 수 없는 카카오 gender 값 - raw: {}", raw);
                return null;
        }
    }
}
