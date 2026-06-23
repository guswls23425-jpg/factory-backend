package com.cafe.cafe_server.weather;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "weather_log", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"log_date", "log_hour"})
})
@Getter
@Setter
public class WeatherLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    // 시간별 누적 (0~23), 일 1회 저장 시 0으로 고정
    @Column(name = "log_hour", nullable = false)
    private Integer logHour;

    @Column(name = "temp")
    private Double temp;

    @Column(name = "feels_like")
    private Double feelsLike;

    @Column(name = "temp_min")
    private Double tempMin;

    @Column(name = "temp_max")
    private Double tempMax;

    @Column(name = "humidity")
    private Integer humidity;

    @Column(name = "wind_speed")
    private Double windSpeed;

    // 날씨 설명 (맑음, 흐림, 비 등)
    @Column(name = "description")
    private String description;

    // OpenWeatherMap 아이콘 코드 (01d, 02d 등)
    @Column(name = "icon")
    private String icon;

    // 날씨 그룹 (Clear, Clouds, Rain 등)
    @Column(name = "weather_main")
    private String weatherMain;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
