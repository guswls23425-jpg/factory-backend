package com.cafe.cafe_server.ai;

import com.cafe.cafe_server.cafatable_x_y.Seat; // 기존 좌석 엔티티 임포트
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_detail_log")
@Getter
@Setter
public class Ai_table {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 💡 어떤 좌석에서 발생한 데이터인지 기존 Seat 테이블과 엮어줌
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false)
    private String status;          // 변환된 DB 저장값 (active / away / available / cleaning)

    @Column(name = "raw_ai_status")
    private String rawAiStatus;     // AI가 보낸 원본 코드 (table_in_use, liquid_spill 등)

    @Column(name = "status_label")
    private String statusLabel;     // AI 한글 라벨 (테이블사용, 자리비움 등)

    @Column(name = "legacy_status")
    private String legacyStatus;    // AI 내부 원본 키 (active, no_drink, spill 등)

    @Column(name = "away_time")
    private String awayTime;

    @Column(name = "status_duration")
    private Integer statusDuration; // 해당 상태가 지속된 시간(초)

    @Column(columnDefinition = "TEXT", name = "raw_json_data")
    private String rawJsonData;     // AI가 보낸 전체 JSON (확장성 보존용)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}