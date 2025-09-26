package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.auth.infrastructure.security.CustomUserDetailService;
import com.gemmap.gemmap.auth.infrastructure.security.UserPrincipal;
import com.gemmap.gemmap.shared.common.enums.ERole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * JWT 인증 처리 Provider
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private final CustomUserDetailService customUserDetailService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        JwtUserInfo jwtUserInfo = new JwtUserInfo(
(Long) authentication.getPrincipal(),
                (ERole) authentication.getCredentials()
        );

        UserPrincipal userPrincipal = (UserPrincipal) customUserDetailService.loadUserByUserId(jwtUserInfo.userId());

        if (userPrincipal.getRole() != jwtUserInfo.role()) {
            throw new AuthenticationException("Invalid Role") {};
        }

        return new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(JwtAuthenticationToken.class);
    }
}