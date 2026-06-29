package com.cafe.cafe_server.kakao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoMessageService {

    private final KakaoTokenRepository tokenRepository;
    private final KakaoAuthService kakaoAuthService;
    private final KakaoNotificationSettingRepository settingRepository;
    private final RestTemplate restTemplate;

    public void sendCleaningAlert(String cafeName, String seatName) {
        KakaoNotificationSetting setting = getSetting(cafeName);
        if (!setting.isCleaningAlert()) return;
        send(cafeName, "🧹 청소 필요 알림", seatName + " 테이블에 청소가 필요합니다.");
    }

    public void sendUnpaidAlert(String cafeName, String seatName) {
        KakaoNotificationSetting setting = getSetting(cafeName);
        if (!setting.isUnpaidAlert()) return;
        send(cafeName, "⚠️ 미주문 고객 알림", seatName + " 테이블에 미주문 고객이 감지되었습니다.");
    }

    public KakaoNotificationSetting getSetting(String cafeName) {
        return settingRepository.findByCafeName(cafeName).orElseGet(() -> {
            KakaoNotificationSetting s = new KakaoNotificationSetting();
            s.setCafeName(cafeName);
            return settingRepository.save(s);
        });
    }

    public KakaoNotificationSetting updateSetting(String cafeName, boolean cleaningAlert, boolean unpaidAlert) {
        KakaoNotificationSetting s = getSetting(cafeName);
        s.setCleaningAlert(cleaningAlert);
        s.setUnpaidAlert(unpaidAlert);
        return settingRepository.save(s);
    }

    private void send(String cafeName, String title, String message) {
        tokenRepository.findByCafeName(cafeName).ifPresentOrElse(token -> {
            try {
                // 토큰 만료 시 갱신
                if (token.getAccessTokenExpiresAt().isBefore(java.time.LocalDateTime.now().plusMinutes(5))) {
                    kakaoAuthService.refreshToken(token);
                    token = tokenRepository.findByCafeName(cafeName).orElse(token);
                }

                String templateObject = String.format(
                    "{\"object_type\":\"text\",\"text\":\"%s\\n%s\",\"link\":{\"web_url\":\"\",\"mobile_web_url\":\"\"}}",
                    title, message
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                headers.setBearerAuth(token.getAccessToken());

                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("template_object", templateObject);

                ResponseEntity<Map> res = restTemplate.exchange(
                    "https://kapi.kakao.com/v2/api/talk/memo/default/send",
                    HttpMethod.POST,
                    new HttpEntity<>(params, headers),
                    Map.class
                );

                if (res.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ 카카오 메시지 전송 성공 (cafe={}, title={})", cafeName, title);
                } else {
                    log.warn("⚠️ 카카오 메시지 전송 실패 (cafe={}): {}", cafeName, res.getStatusCode());
                }
            } catch (Exception e) {
                log.error("카카오 메시지 전송 오류 (cafe={}): {}", cafeName, e.getMessage());
            }
        }, () -> log.warn("카카오 미연동 — 메시지 전송 건너뜀 (cafe={})", cafeName));
    }
}
