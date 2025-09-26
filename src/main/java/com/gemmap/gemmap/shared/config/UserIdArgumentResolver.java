package com.gemmap.gemmap.shared.config;

import com.gemmap.gemmap.auth.infrastructure.security.UserPrincipal;
import com.gemmap.gemmap.shared.common.annotation.UserId;
import com.gemmap.gemmap.shared.exception.CommonException;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @UserId 어노테이션이 붙은 파라미터에 현재 인증된 사용자의 ID를 자동 주입하는 ArgumentResolver
 *
 * - JWT 토큰에서 추출된 사용자 ID를 컨트롤러 메서드 파라미터로 편리하게 전달
 * - SecurityContext에서 인증된 사용자 정보를 자동으로 추출
 * - 컨트롤러에서 @UserId 어노테이션만으로 사용자 ID를 자동 주입받을 수 있음
 */
public class UserIdArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * 해당 파라미터를 처리할 수 있는지 확인
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(UserId.class)
                && parameter.getParameterType().equals(Long.class);
    }

    /**
     * SecurityContext에서 인증된 사용자의 ID를 추출하여 반환
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        if (!(authentication.getPrincipal() instanceof UserPrincipal userPrincipal)) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        if (userPrincipal.getId() == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }

        return userPrincipal.getId();
    }
}