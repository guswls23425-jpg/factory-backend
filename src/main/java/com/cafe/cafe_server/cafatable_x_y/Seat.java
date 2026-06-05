package com.cafe.cafe_server.cafatable_x_y;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cafe_id")
    private Cafe cafe;
}
