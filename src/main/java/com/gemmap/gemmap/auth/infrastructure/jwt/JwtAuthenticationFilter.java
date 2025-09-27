package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.common.enums.ERole;
import com.gemmap.gemmap.shared.util.HeaderUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * JWT 토큰 기반 인증 필터
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtAuthenticationProvider jwtAuthenticationProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws JwtException, ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                processJwtToken(token, request);
            } catch (Exception e) {
                log.error("JWT 인증 실패 - URI: {}, Error: {}", request.getRequestURI(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void processJwtToken(String token, HttpServletRequest request) {
        Claims claims = jwtUtil.validateTokenAndGetClaims(token);

        JwtUserInfo jwtUserInfo = extractUserInfoFromClaims(claims);
        if (jwtUserInfo == null) {
            return;
        }

        authenticateAndSetSecurityContext(jwtUserInfo, request);
    }

    private JwtUserInfo extractUserInfoFromClaims(Claims claims) {
        String userIdClaim = claims.get(Constant.USER_ID_CLAIM_NAME, String.class);
        String userRoleClaim = claims.get(Constant.USER_ROLE_CLAIM_NAME, String.class);

        if (userIdClaim == null || userRoleClaim == null) {
            log.error("JWT Claims에서 필수 정보 누락 - User ID: {}, Role: {}", userIdClaim, userRoleClaim);
            return null;
        }

        return new JwtUserInfo(
                Long.valueOf(userIdClaim),
                ERole.valueOf(userRoleClaim)
        );
    }

    private void authenticateAndSetSecurityContext(JwtUserInfo jwtUserInfo, HttpServletRequest request) {
        JwtAuthenticationToken preAuthentication = new JwtAuthenticationToken(null, jwtUserInfo.userId(), jwtUserInfo.role());
        UsernamePasswordAuthenticationToken authenticated =
                (UsernamePasswordAuthenticationToken) jwtAuthenticationProvider.authenticate(preAuthentication);

        authenticated.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticated);
        SecurityContextHolder.setContext(securityContext);
    }

    private String resolveToken(HttpServletRequest request) {
        return HeaderUtil.refineHeader(request, Constant.AUTHORIZATION_HEADER, Constant.BEARER_PREFIX)
                .orElse(null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return Stream.of(Constant.NO_NEED_AUTH_URLS).anyMatch(pattern ->
            (pattern.endsWith("/**") && uri.startsWith(pattern.replace("/**", ""))) ||
            (pattern.endsWith("/*") && uri.startsWith(pattern.replace("/*", ""))) ||
            pattern.equals(uri)
        );
    }
}