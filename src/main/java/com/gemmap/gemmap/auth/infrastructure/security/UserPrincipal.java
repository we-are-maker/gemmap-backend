package com.gemmap.gemmap.auth.infrastructure.security;


import com.gemmap.gemmap.auth.domain.entity.User;
import com.gemmap.gemmap.shared.common.enums.ERole;
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
 * Spring Security 사용자 인증 정보 객체
 */
@Builder
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPrincipal implements UserDetails {

    @Getter private final Long id;
    @Getter private final ERole role;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .role(user.getRole())
                .authorities(Collections.singleton(new SimpleGrantedAuthority(user.getRole().toSecurityString())))
                .build();
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
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