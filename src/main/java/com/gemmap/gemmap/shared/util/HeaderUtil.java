package com.gemmap.gemmap.shared.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * HTTP 헤더 처리 유틸리티 클래스
 *
 * 주로 인증 관련 헤더에서 토큰을 추출하는 기능을 제공합니다.
 */
public class HeaderUtil {

    /**
     * HTTP 요청 헤더에서 특정 접두사를 제거한 값을 추출합니다.
     *
     * @param httpServletRequest HTTP 요청 객체
     * @param header 추출할 헤더 이름 (예: "Authorization")
     * @param prefix 제거할 접두사 (예: "Bearer ")
     * @return 접두사가 제거된 헤더 값 (Optional)
     * @throws NullPointerException httpServletRequest, header, 또는 prefix가 null인 경우
     *
     * @example
     * <pre>
     * // Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJ... 헤더에서 토큰 추출
     * Optional&lt;String&gt; token = HeaderUtil.refineHeader(request, "Authorization", "Bearer ");
     * // 결과: Optional.of("eyJhbGciOiJIUzI1NiJ9.eyJ...")
     * </pre>
     */
    public static Optional<String> refineHeader(HttpServletRequest httpServletRequest, String header, String prefix){
        String unpreparedToken = httpServletRequest.getHeader(header);

        if(!StringUtils.hasText(unpreparedToken) || !unpreparedToken.startsWith(prefix)){
            return Optional.empty();
        }

        return Optional.of(unpreparedToken.substring(prefix.length()));
    }
}