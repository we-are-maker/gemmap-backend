package com.gemmap.spot.config.security;

import com.gemmap.common.enums.ERole;
import com.gemmap.common.exception.CommonException;
import com.gemmap.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spot 서비스용 UserDetailsService
 *
 * - JWT 토큰 검증만을 위한 구현체
 * - DB 조회 없이 JWT에서 추출한 사용자 ID로 UserDetails 생성
 * - user-service에서 발급한 JWT 토큰의 유효성만 검증하고, 실제 사용자 정보는 조회하지 않음
 *
 * 참고:
 * - 실제 사용자 정보는 user-service에서 관리
 * - spot-service는 JWT 토큰의 서명 검증 및 만료 시간 검증만 수행
 * - JwtAuthenticationFilter와 JwtAuthenticationProvider가 JWT 검증 후 이 서비스를 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotUserDetailsService implements UserDetailsService {

    /**
     * JWT에서 추출한 사용자 ID로 UserDetails 생성
     *
     * @param username JWT에서 추출한 사용자 ID (문자열)
     * @return SpotUserPrincipal 객체
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            Long userId = Long.parseLong(username);

            // JWT에서 추출한 ID로 UserPrincipal 생성
            // 기본 권한은 USER로 설정 (JWT에서 role도 추출 가능하도록 개선 필요 시 수정 가능)
            return SpotUserPrincipal.create(userId, ERole.USER);

        } catch (NumberFormatException e) {
            log.error("Invalid user ID format in JWT: {}", username);
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }
    }
}
