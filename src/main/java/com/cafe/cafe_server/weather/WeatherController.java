package com.cafe.cafe_server.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(originPatterns = "*")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    // 오늘 날씨 (최신)
    @GetMapping("/today")
    public ResponseEntity<?> today() {
        return weatherService.getTodayLatest()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // 특정 날짜 대표 날씨
    @GetMapping("/daily")
    public ResponseEntity<?> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return weatherService.getDailyRepresentative(date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // 날짜 범위 조회 (분석 페이지용)
    @GetMapping("/range")
    public ResponseEntity<List<WeatherLog>> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(weatherService.getByDateRange(from, to));
    }

    // 즉시 수집 트리거 (테스트용)
    @PostMapping("/fetch-now")
    public ResponseEntity<Map<String, String>> fetchNow() {
        weatherService.fetchAndSaveWeather();
        return ResponseEntity.ok(Map.of("result", "날씨 수집 요청 완료"));
    }
}
