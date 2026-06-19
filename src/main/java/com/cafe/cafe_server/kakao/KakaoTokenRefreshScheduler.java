package com.cafe.cafe_server.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoTokenRefreshScheduler {

    private final KakaoTokenRepository tokenRepository;
    private final KakaoAuthService kakaoAuthService;

    // 1시간마다 실행 — 액세스 토큰이 30분 이내에 만료되는 항목 갱신
    @Scheduled(fixedRate = 3600_000)
    public void refreshExpiringSoonTokens() {
        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(30);
        LocalDateTime now    = LocalDateTime.now();

        List<KakaoToken> targets = tokenRepository
                .findByAccessTokenExpiresAtBeforeAndRefreshTokenExpiresAtAfter(cutoff, now);

        if (targets.isEmpty()) return;

        log.info("카카오 토큰 자동 갱신 대상: {}건", targets.size());
        for (KakaoToken token : targets) {
            kakaoAuthService.refreshToken(token);
        }
    }
}
