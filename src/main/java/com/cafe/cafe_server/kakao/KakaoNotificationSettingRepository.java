package com.cafe.cafe_server.kakao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface KakaoNotificationSettingRepository extends JpaRepository<KakaoNotificationSetting, Long> {
    Optional<KakaoNotificationSetting> findByCafeName(String cafeName);
}
