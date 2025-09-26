package com.gemmap.gemmap.auth.infrastructure.jwt;

import com.gemmap.gemmap.shared.common.constants.Constant;
import com.gemmap.gemmap.shared.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * JWT 예외 처리 필터
 */
@Slf4j
public class JwtExceptionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } catch (SecurityException e) {
            log.error("FilterException throw SecurityException Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.ACCESS_DENIED);
            filterChain.doFilter(request, response);
        } catch (MalformedJwtException e) {
            log.error("FilterException throw MalformedJwtException Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.TOKEN_MALFORMED);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            log.error("FilterException throw IllegalArgumentException Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.INVALID_TOKEN);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.error("FilterException throw ExpiredJwtException Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.EXPIRED_TOKEN);
            filterChain.doFilter(request, response);
        } catch (UnsupportedJwtException e) {
            log.error("FilterException throw UnsupportedJwtException Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.TOKEN_UNSUPPORTED);
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.error("FilterException throw JwtException Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.TOKEN_UNKNOWN);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("FilterException throw Exception Exception : {}", e.getMessage());
            request.setAttribute("exception", ErrorCode.INTERNAL_SERVER_ERROR);
            filterChain.doFilter(request, response);
        }
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