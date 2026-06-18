package com.cafe.cafe_server.ai;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Getter
public class LogResponseDto {

    private final Long          id;
    private final Long          seatId;
    private final String        seatName;
    private final String        status;
    private final String        rawAiStatus;
    private final Integer       occupantCount;
    private final String        awayTime;
    private final Long          awayDurationSeconds;
    private final Long          statusDurationSeconds;
    private final Double        colorChangeRatio;
    private final OffsetDateTime spillDetectedAt;
    private final String        events;
    private final LocalDateTime createdAt;

    public LogResponseDto(Ai_table log) {
        this.id                   = log.getId();
        this.seatId               = log.getSeat().getId();
        this.seatName             = log.getSeat().getName();
        this.status               = log.getStatus();
        this.rawAiStatus          = log.getRawAiStatus();
        this.occupantCount        = log.getOccupantCount();
        this.awayTime             = log.getAwayTime();
        this.awayDurationSeconds  = log.getAwayDurationSeconds();
        this.statusDurationSeconds= log.getStatusDurationSeconds();
        this.colorChangeRatio     = log.getColorChangeRatio();
        this.spillDetectedAt      = log.getSpillDetectedAt();
        this.events               = log.getEvents();
        this.createdAt            = log.getCreatedAt();
    }
}
