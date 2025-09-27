package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.shared.common.enums.ERole;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * JWT 기반 Spring Security 인증 토큰
 */
public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private Long userId;
    private ERole role;

    public JwtAuthenticationToken(Long userId, ERole role) {
        super(null);
        this.userId = userId;
        this.role = role;
        setAuthenticated(false);
    }

    public JwtAuthenticationToken(Collection<? extends GrantedAuthority> authorities,
                                  Long userId, ERole role) {
        super(authorities);
        this.userId = userId;
        this.role = role;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.role;
    }

    @Override
    public Object getPrincipal() {
        return this.userId;
    }
}