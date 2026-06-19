package com.cafe.cafe_server.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final KakaoTokenRepository tokenRepository;
    private final RestTemplate restTemplate;

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.client-secret}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    // ── 인가 코드 → 액세스 토큰 교환 후 DB 저장 ────────────────────────────────
    @Transactional
    public void exchangeAndSave(String code, String cafeName) {
        Map<String, Object> tokenResponse = requestToken("authorization_code", code, null);
        saveToken(cafeName, tokenResponse);
        log.info("카카오 토큰 저장 완료 (cafe={})", cafeName);
    }

    // ── 리프레시 토큰으로 액세스 토큰 갱신 ──────────────────────────────────────
    @Transactional
    public void refreshToken(KakaoToken token) {
        try {
            Map<String, Object> response = requestToken("refresh_token", null, token.getRefreshToken());
            // 액세스 토큰 갱신
            token.setAccessToken((String) response.get("access_token"));
            int expiresIn = ((Number) response.get("expires_in")).intValue();
            token.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            // 리프레시 토큰도 새로 발급된 경우 갱신
            if (response.containsKey("refresh_token")) {
                token.setRefreshToken((String) response.get("refresh_token"));
                int rtExp = ((Number) response.get("refresh_token_expires_in")).intValue();
                token.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(rtExp));
            }
            token.setUpdatedAt(LocalDateTime.now());
            tokenRepository.save(token);
            log.info("카카오 토큰 갱신 완료 (cafe={})", token.getCafeName());
        } catch (Exception e) {
            log.error("카카오 토큰 갱신 실패 (cafe={}): {}", token.getCafeName(), e.getMessage());
        }
    }

    // ── 상태 조회 ─────────────────────────────────────────────────────────────
    public boolean isConnected(String cafeName) {
        return tokenRepository.findByCafeName(cafeName)
                .map(t -> t.getRefreshTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    public Optional<KakaoToken> getToken(String cafeName) {
        return tokenRepository.findByCafeName(cafeName);
    }

    // ── 카카오 token API 호출 공통 ────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Object> requestToken(String grantType, String code, String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", grantType);
        params.add("client_id", restApiKey);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        if (code != null)         params.add("code", code);
        if (refreshToken != null) params.add("refresh_token", refreshToken);

        try {
            ResponseEntity<Map> res = restTemplate.exchange(
                    "https://kauth.kakao.com/oauth/token",
                    HttpMethod.POST,
                    new HttpEntity<>(params, headers),
                    Map.class
            );
            if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
                throw new RuntimeException("카카오 토큰 API 오류: " + res.getStatusCode());
            }
            return res.getBody();
        } catch (HttpClientErrorException e) {
            log.error("카카오 토큰 API 오류 상세 — status={} body={} requestParams={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), params);
            throw new RuntimeException("카카오 토큰 API 오류: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    // ── 토큰 응답 → DB 저장/업데이트 ────────────────────────────────────────────
    private void saveToken(String cafeName, Map<String, Object> response) {
        KakaoToken token = tokenRepository.findByCafeName(cafeName)
                .orElseGet(() -> { KakaoToken t = new KakaoToken(); t.setCafeName(cafeName); return t; });

        token.setAccessToken((String) response.get("access_token"));
        token.setRefreshToken((String) response.get("refresh_token"));

        int expiresIn   = ((Number) response.get("expires_in")).intValue();
        int rtExpiresIn = ((Number) response.get("refresh_token_expires_in")).intValue();
        token.setAccessTokenExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        token.setRefreshTokenExpiresAt(LocalDateTime.now().plusSeconds(rtExpiresIn));
        token.setUpdatedAt(LocalDateTime.now());

        tokenRepository.save(token);
    }
}
