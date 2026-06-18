package com.cafe.cafe_server.ai;

import com.cafe.cafe_server.cafatable_x_y.Seat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_detail_log")
@Getter
@Setter
public class Ai_table {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false)
    private String status;           // 정규화된 상태 (active / away / available / cleaning)

    @Column(name = "raw_ai_status")
    private String rawAiStatus;      // AI 원본 코드 (WARNING, OCCUPIED 등)

    @Column(name = "occupant_count")
    private Integer occupantCount;   // AI가 감지한 인원 수

    @Column(name = "away_time")
    private String awayTime;         // 자리비움 시간 (포맷 문자열)

    @Column(name = "away_duration_seconds")
    private Long awayDurationSeconds; // 자리비움 지속 시간(초)

    @Column(name = "status_duration_seconds")
    private Long statusDurationSeconds; // 현재 상태 지속 시간(초)

    @Column(name = "color_change_ratio")
    private Double colorChangeRatio; // 색상 변화 비율 (스필 감지용)

    @Column(name = "spill_detected_at")
    private OffsetDateTime spillDetectedAt; // 스필 감지 시각

    @Column(columnDefinition = "TEXT", name = "events")
    private String events;           // AI 이벤트 목록 (JSON 배열 문자열)

    @Column(columnDefinition = "TEXT", name = "raw_json_data")
    private String rawJsonData;      // AI 전체 원본 JSON

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
