package com.gemmap.user.auth.infrastructure.oauth;

import com.gemmap.user.auth.application.dto.kakao.KakaoTokenResponse;
import com.gemmap.user.auth.application.dto.kakao.KakaoUserInfoResponse;
import com.gemmap.common.exception.CommonException;
import com.gemmap.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 OAuth2 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuth2Service {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${oauth2.kakao.client-id}")
    private String clientId;

    @Value("${oauth2.kakao.client-secret:}")
    private String clientSecret;

    @Value("${oauth2.kakao.redirect-uri}")
    private String redirectUri;

    private static final String KAUTH_HOST = "https://kauth.kakao.com";
    private static final String KAPI_HOST = "https://kapi.kakao.com";
    private static final String TOKEN_ENDPOINT = "/oauth/token";
    private static final String USER_INFO_ENDPOINT = "/v2/user/me";
    private static final String LOGOUT_ENDPOINT = "/v1/user/logout";
    private static final String AUTHORIZE_ENDPOINT = "/oauth/authorize";
    private static final String DEFAULT_SCOPE = "profile_nickname profile_image account_email name gender age_range birthday birthyear";
    private static final String PROPERTY_KEYS = "[\"kakao_account.profile\", \"kakao_account.email\"]";

    /**
     * 카카오 인가 코드 요청 URL 생성
     */
    public String getAuthorizationUrl() {
        String authUrl = UriComponentsBuilder
                .fromUriString(KAUTH_HOST + AUTHORIZE_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", DEFAULT_SCOPE)
                .build()
                .encode()
                .toUriString();

        log.info("카카오 인가 URL 생성:" + authUrl);
        return authUrl;
    }

    /**
     * 인가 코드로 Access Token 획득
     */
    public KakaoTokenResponse getAccessToken(String authorizationCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            params.add("client_secret", clientSecret);
        }
        params.add("code", authorizationCode);
        params.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<KakaoTokenResponse> response = restTemplate.exchange(
                    KAUTH_HOST + TOKEN_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    KakaoTokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            log.error("카카오 토큰 발급 실패: {}", response.getStatusCode());
            throw new CommonException(ErrorCode.KAKAO_TOKEN_REQUEST_FAILED);

        } catch (Exception e) {
            log.error("카카오 토큰 발급 중 오류 발생: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.KAKAO_TOKEN_REQUEST_FAILED);
        }
    }

    /**
     * Access Token으로 사용자 정보 조회
     */
    public KakaoUserInfoResponse getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth(accessToken);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("property_keys", PROPERTY_KEYS);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<KakaoUserInfoResponse> response = restTemplate.exchange(
                    KAPI_HOST + USER_INFO_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    KakaoUserInfoResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            log.error("카카오 사용자 정보 조회 실패: {}", response.getStatusCode());
            throw new CommonException(ErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);

        } catch (Exception e) {
            log.error("카카오 사용자 정보 조회 중 오류 발생: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.KAKAO_USER_INFO_REQUEST_FAILED);
        }
    }

    /**
     * 토큰 갱신
     */
    public KakaoTokenResponse refreshToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "refresh_token");
        params.add("client_id", clientId);
        if (clientSecret != null && !clientSecret.isEmpty()) {
            params.add("client_secret", clientSecret);
        }
        params.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<KakaoTokenResponse> response = restTemplate.exchange(
                    KAUTH_HOST + TOKEN_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    KakaoTokenResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            log.error("카카오 토큰 갱신 실패: {}", response.getStatusCode());
            throw new CommonException(ErrorCode.KAKAO_TOKEN_REFRESH_FAILED);

        } catch (Exception e) {
            log.error("카카오 토큰 갱신 중 오류 발생: {}", e.getMessage(), e);
            throw new CommonException(ErrorCode.KAKAO_TOKEN_REFRESH_FAILED);
        }
    }

    /**
     * 카카오 로그아웃 API 호출
     */
    public void logout(String kakaoAccessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBearerAuth(kakaoAccessToken);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    KAPI_HOST + LOGOUT_ENDPOINT,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("카카오 로그아웃 API 호출 성공");
            } else {
                log.warn("카카오 로그아웃 API 응답 상태 코드: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.warn("카카오 로그아웃 API 호출 실패 (계속 진행): {}", e.getMessage());
            // 카카오 로그아웃 실패는 서비스 로그아웃에 영향을 주지 않음
        }
    }
}
