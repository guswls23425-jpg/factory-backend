package com.cafe.cafe_server.cafatable_x_y;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeatDto {
    private Long id;
    private String name;
    private String status;
    private String awayTime;
    private Integer posX;
    private Integer posY;
    private Integer personCount;
    private Integer floorNumber; // 층 번호 (기본값 1) — 신규
    private String floorName;    // 층 이름 (기본값 "1층") — 기존 GCP 호환
}
