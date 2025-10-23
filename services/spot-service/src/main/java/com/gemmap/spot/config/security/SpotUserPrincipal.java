package com.gemmap.spot.config.security;

import com.gemmap.common.enums.ERole;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spot 서비스용 Spring Security 사용자 인증 정보 객체
 * - JWT 토큰에서 추출한 사용자 정보를 담는 UserDetails 구현체
 * - DB 조회 없이 JWT 토큰 정보만으로 인증 처리
 */
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class SpotUserPrincipal implements UserDetails {

    @Getter
    private final Long id;

    @Getter
    private final ERole role;

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * JWT에서 추출한 정보로 UserPrincipal 생성
     */
    public static SpotUserPrincipal create(Long userId, ERole role) {
        return SpotUserPrincipal.builder()
                .id(userId)
                .role(role)
                .authorities(Collections.singleton(new SimpleGrantedAuthority(role.toSecurityString())))
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;  // JWT 기반 인증이므로 password 불필요
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
