package com.gemmap.user.auth.infrastructure.security;

import com.gemmap.user.auth.domain.entity.User;
import com.gemmap.user.auth.domain.repository.UserRepository;

import com.gemmap.common.exception.CommonException;
import com.gemmap.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * JWT 인증을 위한 사용자 정보 조회 서비스
 */
@Service
@RequiredArgsConstructor
@Getter
@Slf4j
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetails loadUserByUserId(Long userId) throws CommonException {
        final User user = userRepository.findByIdAndIsLoginAndRefreshTokenIsNotNull(userId, true)
                .orElseThrow(() -> new CommonException(ErrorCode.USER_NOT_FOUND));

        return UserPrincipal.create(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadUserByUserId(Long.parseLong(username));
    }
}
