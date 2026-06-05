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

    private String name;        // 예: "테이블 1"
    private String status;      // "active", "away", "available"
    private String awayTime;    // 자리비움 시간 (null 가능)

    @Column(name = "pos_x")
    private Integer posX;       // 드래그 앤 드롭 X 좌표

    @Column(name = "pos_y")
    private Integer posY;       // 드래그 앤 드롭 Y 좌표

    @Column(name = "person_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer personCount = 0; // 테이블에 앉은 사람 수 (0~4)

    @Column(name = "floor_name", columnDefinition = "VARCHAR(50) DEFAULT '1층'")
    private String floorName = "1층"; // 층 이름 (1층, 2층, ...)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cafe_id")
    private Cafe cafe;          // 어느 카페의 좌석인지 연결 (FK)
}