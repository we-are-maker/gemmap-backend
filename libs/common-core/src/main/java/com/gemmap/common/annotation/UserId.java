package com.gemmap.common.annotation;

import java.lang.annotation.*;

/**
 * 현재 인증된 사용자의 ID를 자동으로 주입받는 어노테이션
 *
 * - 컨트롤러 메서드 파라미터에 사용하여 현재 사용자 ID 자동 주입
 * - UserIdArgumentResolver와 연동하여 SecurityContext에서 사용자 정보 추출
 * - JWT 토큰 기반 인증과 연동하여 편리한 사용자 정보 접근 제공
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserId {
}
