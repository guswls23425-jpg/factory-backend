package com.cafe.cafe_server.cafatable_x_y;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FloorDto {
    private Integer floorNumber;
    private String label;
    private List<SeatDto> seats;
    private List<RestroomMarkerDto> restrooms;
    private List<WindowMarkerDto>   windows;

    @Getter @Setter
    public static class RestroomMarkerDto {
        private Integer id;
        private String  type;  // "male" | "female" | "both"
        private Integer posX;
        private Integer posY;
    }

    @Getter @Setter
    public static class WindowMarkerDto {
        private Integer id;
        private Integer posX;
        private Integer posY;
        private Double  angle;
        private Integer length;
    }
}
