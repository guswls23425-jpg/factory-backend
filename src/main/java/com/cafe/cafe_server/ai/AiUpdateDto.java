package com.cafe.cafe_server.ai;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class AiUpdateDto {
    private String schemaVersion;
    private String cafeName;
    private Long floorId;
    private String sourceId;
    private OffsetDateTime detectedAt;
    private List<SeatUpdate> seats;

    @Getter
    @Setter
    public static class SeatUpdate {
        private Integer seatNumber;
        private String status;
        private Integer occupantCount;
        private List<Long> personIds;
        private Long statusDurationSeconds;
        private Long awayDurationSeconds;
        private List<String> events;
        private Double colorChangeRatio;
        private OffsetDateTime spillDetectedAt;
    }
}
