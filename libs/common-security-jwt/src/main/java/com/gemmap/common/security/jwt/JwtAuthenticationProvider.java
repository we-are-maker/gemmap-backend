package com.gemmap.common.security.jwt;

import com.gemmap.common.enums.ERole;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * JWT 인증 처리 Provider
 * UserDetailsService 빈이 존재하는 서비스에서만 자동 등록됨
 */
@Component
@ConditionalOnBean(UserDetailsService.class)
@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        JwtUserInfo jwtUserInfo = new JwtUserInfo(
                (Long) authentication.getPrincipal(),
                (ERole) authentication.getCredentials()
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(jwtUserInfo.userId()));

        // Role 검증은 각 서비스에서 구현
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(JwtAuthenticationToken.class);
    }
}
