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
    private String status; 

    @Column(name = "away_time")
    private String awayTime; 

    // 💡 나중에 어떤 가변 데이터(나이, 성별 등)가 들어올지 모르니 JSON 통째로 담을 텍스트 공간
    @Column(columnDefinition = "TEXT", name = "raw_json_data")
    private String rawJsonData;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}