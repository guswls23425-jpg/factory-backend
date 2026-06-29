package com.cafe.cafe_server.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/kakao")
@RequiredArgsConstructor
public class KakaoAuthController {

    private final KakaoAuthService kakaoAuthService;
    private final KakaoMessageService kakaoMessageService;

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.frontend-url}")
    private String frontendUrl;

    // ── 프론트에서 로그인 URL 요청 ──────────────────────────────────────────────
    // GET /api/auth/kakao/login-url?cafeName=xxx
    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> getLoginUrl(@RequestParam String cafeName) {
        // state에 cafeName을 Base64로 담아 콜백에서 복원
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(
                cafeName.getBytes(StandardCharsets.UTF_8));

        String url = "https://kauth.kakao.com/oauth/authorize"
                + "?client_id=" + restApiKey
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&state=" + state;

        return ResponseEntity.ok(Map.of("url", url));
    }

    // ── 카카오 인증 콜백 ─────────────────────────────────────────────────────────
    // GET /api/auth/kakao/callback?code=xxx&state=xxx
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state) {

        if (error != null || code == null) {
            log.warn("카카오 인증 실패: {}", error);
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "?kakao=error"))
                    .build();
        }

        try {
            // state에서 cafeName 복원
            String cafeName = new String(
                    Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            kakaoAuthService.exchangeAndSave(code, cafeName);
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "?kakao=success"))
                    .build();
        } catch (Exception e) {
            log.error("카카오 토큰 교환 실패: {}", e.getMessage());
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "?kakao=error"))
                    .build();
        }
    }

    // ── 연결 상태 조회 ────────────────────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(@RequestParam String cafeName) {
        boolean connected = kakaoAuthService.isConnected(cafeName);
        return ResponseEntity.ok(Map.of("connected", connected));
    }

    // ── 연동 해제 ─────────────────────────────────────────────────────────────────
    @DeleteMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@RequestParam String cafeName) {
        kakaoAuthService.disconnect(cafeName);
        return ResponseEntity.noContent().build();
    }

    // ── 알림 설정 조회 ────────────────────────────────────────────────────────────
    @GetMapping("/notification-setting")
    public ResponseEntity<Map<String, Object>> getNotificationSetting(@RequestParam String cafeName) {
        var s = kakaoMessageService.getSetting(cafeName);
        return ResponseEntity.ok(Map.of(
            "cleaningAlert", s.isCleaningAlert(),
            "unpaidAlert", s.isUnpaidAlert()
        ));
    }

    // ── 알림 설정 저장 ────────────────────────────────────────────────────────────
    @PostMapping("/notification-setting")
    public ResponseEntity<Map<String, Object>> updateNotificationSetting(
            @RequestParam String cafeName,
            @RequestParam boolean cleaningAlert,
            @RequestParam boolean unpaidAlert) {
        var s = kakaoMessageService.updateSetting(cafeName, cleaningAlert, unpaidAlert);
        return ResponseEntity.ok(Map.of(
            "cleaningAlert", s.isCleaningAlert(),
            "unpaidAlert", s.isUnpaidAlert()
        ));
    }
}
