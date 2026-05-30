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
    private Integer posX;  // ← 직접 flat하게
    private Integer posY;  // ← 직접 flat하게
    // Position 클래스 통째로 삭제
}