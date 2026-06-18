package com.cafe.cafe_server.cafatable_x_y;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "seat")
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long id;

    private String name;
    private String status;
    private String awayTime;

    @Column(name = "pos_x")
    private Integer posX;

    @Column(name = "pos_y")
    private Integer posY;

    @Column(name = "person_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer personCount = 0;

    // 층 번호 (정수) — 신규 방식
    @Column(name = "floor_number", nullable = false, columnDefinition = "INT DEFAULT 1")
    private Integer floorNumber = 1;

    // 층 이름 (문자열) — 기존 GCP 호환용
    @Column(name = "floor_name", columnDefinition = "VARCHAR(50) DEFAULT '1층'")
    private String floorName = "1층";

    // ── 테이블 레이아웃 (관리자 편집) ─────────────────────────────────────────
    @Column(name = "shape")
    private String shape;                       // rect | rounded | circle

    @Column(name = "table_width")
    private Integer tableWidth;

    @Column(name = "table_height")
    private Integer tableHeight;

    @Column(name = "capacity")
    private Integer capacity;                   // 의자 수 (1~8)

    @Column(name = "rotation")
    private Integer rotation;                   // 회전 각도 (0~359)

    @Column(name = "chair_angles", columnDefinition = "TEXT")
    private String chairAngles;                 // JSON 배열 문자열 "[1.57, 3.14, ...]"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cafe_id")
    private Cafe cafe;

    // Seat.java 하단에 이 코드를 추가해두면 나중에 삭제 충돌이 일어나지 않습니다.
    @OneToMany(mappedBy = "seat", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<com.cafe.cafe_server.ai.Ai_table> aiLogs = new ArrayList<>();
}
