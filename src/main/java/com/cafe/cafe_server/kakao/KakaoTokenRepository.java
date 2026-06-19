package com.cafe.cafe_server.kakao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KakaoTokenRepository extends JpaRepository<KakaoToken, Long> {
    Optional<KakaoToken> findByCafeName(String cafeName);
    // 액세스 토큰 만료 30분 전 이내이고 리프레시 토큰은 아직 유효한 것들
    List<KakaoToken> findByAccessTokenExpiresAtBeforeAndRefreshTokenExpiresAtAfter(
            LocalDateTime accessExpiryCutoff, LocalDateTime now);
}
