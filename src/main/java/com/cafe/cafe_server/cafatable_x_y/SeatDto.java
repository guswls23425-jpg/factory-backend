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
    private Integer personCount; // 테이블에 앉은 사람 수 (0~4)
}