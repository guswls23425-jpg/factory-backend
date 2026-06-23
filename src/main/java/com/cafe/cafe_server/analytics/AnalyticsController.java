package com.cafe.cafe_server.analytics;

import com.cafe.cafe_server.ai.Ai_db_Repository;
import com.cafe.cafe_server.ai.Ai_table;
import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
import com.cafe.cafe_server.cafatable_x_y.Seat;
import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
import com.cafe.cafe_server.weather.WeatherLog;
import com.cafe.cafe_server.weather.WeatherLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(originPatterns = "*")
@RequiredArgsConstructor
public class AnalyticsController {

    private final Ai_db_Repository aiLogRepository;
    private final SeatRepository seatRepository;
    private final CafeRepository cafeRepository;
    private final WeatherLogRepository weatherLogRepository;

    // 날짜별 좌석 점유율 + 위치 정보
    @GetMapping("/daily-occupancy")
    public ResponseEntity<List<Map<String, Object>>> dailyOccupancy(
            @RequestParam String cafeName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") Integer floorId) {

        var cafeOpt = cafeRepository.findByName(cafeName);
        if (cafeOpt.isEmpty()) return ResponseEntity.notFound().build();

        Long cafeId = cafeOpt.get().getId();
        List<Seat> seats = seatRepository.findByCafeIdAndFloorNumber(cafeId, floorId);

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.atTime(LocalTime.MAX);
        List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(cafeName, from, to);

        // seatId별로 로그 그룹화
        Map<Long, List<Ai_table>> logsBySeat = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getSeat().getId()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Seat seat : seats) {
            List<Ai_table> seatLogs = logsBySeat.getOrDefault(seat.getId(), List.of());
            long activeCount = seatLogs.stream()
                    .filter(l -> "active".equals(l.getStatus()))
                    .count();
            long total = seatLogs.size();
            double occupancy = total == 0 ? 0.0 : Math.round((activeCount * 100.0 / total) * 10) / 10.0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seatId", seat.getId());
            item.put("name", seat.getName());
            item.put("posX", seat.getPosX());
            item.put("posY", seat.getPosY());
            item.put("tableWidth", seat.getTableWidth());
            item.put("tableHeight", seat.getTableHeight());
            item.put("shape", seat.getShape());
            item.put("rotation", seat.getRotation());
            item.put("occupancy", occupancy);
            item.put("activeCount", activeCount);
            item.put("totalCount", total);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // 날씨 유형별 평균 혼잡도
    @GetMapping("/weather-congestion")
    public ResponseEntity<List<Map<String, Object>>> weatherCongestion(
            @RequestParam String cafeName,
            @RequestParam(defaultValue = "30") int days) {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);

        // 날짜별 혼잡도 계산 (active 비율)
        Map<LocalDate, Double> dailyOccupancy = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(
                    cafeName, d.atStartOfDay(), d.atTime(LocalTime.MAX));
            if (logs.isEmpty()) continue;
            long active = logs.stream().filter(l -> "active".equals(l.getStatus())).count();
            dailyOccupancy.put(d, active * 100.0 / logs.size());
        }

        // 날짜별 대표 날씨 조회
        Map<String, List<Double>> byWeather = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Double> e : dailyOccupancy.entrySet()) {
            Optional<WeatherLog> noonOpt = weatherLogRepository.findByLogDateAndLogHour(e.getKey(), 12);
            Optional<WeatherLog> anyOpt  = weatherLogRepository.findTopByLogDateOrderByLogHourAsc(e.getKey());
            WeatherLog wl = noonOpt.orElse(anyOpt.orElse(null));
            if (wl == null) continue;
            String key = wl.getWeatherMain();
            byWeather.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getValue());
        }

        Map<String, String> labelMap = Map.of(
                "Clear", "맑음", "Clouds", "흐림", "Rain", "비",
                "Drizzle", "이슬비", "Snow", "눈", "Thunderstorm", "천둥번개",
                "Mist", "안개", "Fog", "안개", "Haze", "연무", "Dust", "황사");
        Map<String, String> colorMap = Map.of(
                "Clear", "#f59e0b", "Clouds", "#94a3b8", "Rain", "#3b82f6",
                "Drizzle", "#60a5fa", "Snow", "#bae6fd", "Thunderstorm", "#7c3aed",
                "Mist", "#cbd5e1", "Fog", "#cbd5e1", "Haze", "#d1d5db", "Dust", "#d97706");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : byWeather.entrySet()) {
            String main = e.getKey();
            double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("weatherMain", main);
            item.put("name", labelMap.getOrDefault(main, main));
            item.put("value", Math.round(avg * 10) / 10.0);
            item.put("color", colorMap.getOrDefault(main, "#6b7280"));
            item.put("dayCount", e.getValue().size());
            result.add(item);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("value"), (Double) a.get("value")));
        return ResponseEntity.ok(result);
    }
}
