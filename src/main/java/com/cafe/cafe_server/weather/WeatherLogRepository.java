package com.cafe.cafe_server.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeatherLogRepository extends JpaRepository<WeatherLog, Long> {

    Optional<WeatherLog> findByLogDateAndLogHour(LocalDate date, Integer hour);

    List<WeatherLog> findByLogDateBetweenOrderByLogDateAscLogHourAsc(LocalDate from, LocalDate to);

    List<WeatherLog> findByLogDateOrderByLogHourAsc(LocalDate date);

    // 날짜별 대표값: 정오(12시) 기록 우선, 없으면 첫 번째 값
    Optional<WeatherLog> findTopByLogDateOrderByLogHourAsc(LocalDate date);
}
