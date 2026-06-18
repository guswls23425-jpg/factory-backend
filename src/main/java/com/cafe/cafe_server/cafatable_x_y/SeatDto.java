package com.cafe.cafe_server.cafatable_x_y;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

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
    private Integer floorNumber;
    private String floorName;

    // 테이블 레이아웃
    private String shape;
    private Integer tableWidth;
    private Integer tableHeight;
    private Integer capacity;
    private Integer rotation;
    private List<Double> chairAngles;
}
