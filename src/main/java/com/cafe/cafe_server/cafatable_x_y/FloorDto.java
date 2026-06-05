package com.cafe.cafe_server.cafatable_x_y;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FloorDto {
    private Integer floorNumber; // 1, 2, 3 ...
    private String label;        // "1층", "2층" ...
    private List<SeatDto> seats;
}
