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
import java.util.TreeMap;

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

        // weatherMain → 5개 카테고리로 정규화
        Map<String, String> normalize = new HashMap<>();
        normalize.put("Clear",        "맑음");
        normalize.put("Clouds",       "흐림");
        normalize.put("Rain",         "비");
        normalize.put("Drizzle",      "비");
        normalize.put("Thunderstorm", "비");
        normalize.put("Snow",         "눈");
        normalize.put("Dust",         "황사");
        normalize.put("Sand",         "황사");
        normalize.put("Haze",         "황사");
        normalize.put("Mist",         "흐림");
        normalize.put("Fog",          "흐림");
        normalize.put("Smoke",        "흐림");
        normalize.put("Ash",          "흐림");
        normalize.put("Squall",       "비");
        normalize.put("Tornado",      "비");

        // 날짜별 대표 날씨 조회
        Map<String, List<Double>> byWeather = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Double> e : dailyOccupancy.entrySet()) {
            Optional<WeatherLog> noonOpt = weatherLogRepository.findByLogDateAndLogHour(e.getKey(), 12);
            Optional<WeatherLog> anyOpt  = weatherLogRepository.findTopByLogDateOrderByLogHourAsc(e.getKey());
            WeatherLog wl = noonOpt.orElse(anyOpt.orElse(null));
            if (wl == null) continue;
            String key = normalize.getOrDefault(wl.getWeatherMain(), "흐림");
            byWeather.computeIfAbsent(key, k -> new ArrayList<>()).add(e.getValue());
        }

        // 5개 카테고리 색상 (key = 정규화된 한글명)
        Map<String, String> colorMap = new HashMap<>();
        colorMap.put("맑음", "#f59e0b");
        colorMap.put("흐림", "#94a3b8");
        colorMap.put("비",   "#3b82f6");
        colorMap.put("눈",   "#bae6fd");
        colorMap.put("황사", "#d97706");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : byWeather.entrySet()) {
            String name = e.getKey(); // 이미 한글 정규화된 값
            double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("value", Math.round(avg * 10) / 10.0);
            item.put("color", colorMap.getOrDefault(name, "#6b7280"));
            item.put("dayCount", e.getValue().size());
            result.add(item);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("value"), (Double) a.get("value")));
        return ResponseEntity.ok(result);
    }

    // 날짜별 층별 점유율 요약
    @GetMapping("/floor-summary")
    public ResponseEntity<List<Map<String, Object>>> floorSummary(
            @RequestParam String cafeName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        var cafeOpt = cafeRepository.findByName(cafeName);
        if (cafeOpt.isEmpty()) return ResponseEntity.notFound().build();

        Long cafeId = cafeOpt.get().getId();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.atTime(LocalTime.MAX);
        List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(cafeName, from, to);
        Map<Long, List<Ai_table>> logsBySeat = logs.stream()
                .collect(Collectors.groupingBy(l -> l.getSeat().getId()));

        // 등록된 모든 층 번호 수집
        List<Seat> allSeats = seatRepository.findByCafeId(cafeId);
        Map<Integer, String> floorNames = new LinkedHashMap<>();
        allSeats.forEach(s -> floorNames.putIfAbsent(s.getFloorNumber(), s.getFloorName()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Integer, String> fl : new TreeMap<>(floorNames).entrySet()) {
            List<Seat> floorSeats = allSeats.stream()
                    .filter(s -> fl.getKey().equals(s.getFloorNumber()))
                    .toList();
            long totalActive = 0, totalLogs = 0;
            for (Seat seat : floorSeats) {
                List<Ai_table> sl = logsBySeat.getOrDefault(seat.getId(), List.of());
                totalLogs  += sl.size();
                totalActive += sl.stream().filter(l -> "active".equals(l.getStatus())).count();
            }
            double occ = totalLogs == 0 ? 0 : Math.round(totalActive * 1000.0 / totalLogs) / 10.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("floorNumber", fl.getKey());
            item.put("floorName",   fl.getValue());
            item.put("occupancy",   occ);
            item.put("seatCount",   floorSeats.size());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // 테이블별 평균 체류 시간
    @GetMapping("/stay-duration")
    public ResponseEntity<List<Map<String, Object>>> stayDuration(
            @RequestParam String cafeName,
            @RequestParam(defaultValue = "30") int days) {

        var cafeOpt = cafeRepository.findByName(cafeName);
        if (cafeOpt.isEmpty()) return ResponseEntity.notFound().build();

        Long cafeId = cafeOpt.get().getId();
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);

        List<Ai_table> allLogs = aiLogRepository.findByCafeNameAndDateRange(
                cafeName, from.atStartOfDay(), to.atTime(LocalTime.MAX));

        // 좌석별 로그 그룹화 후 시간순 정렬
        Map<Long, List<Ai_table>> logsBySeat = allLogs.stream()
                .collect(Collectors.groupingBy(l -> l.getSeat().getId()));

        List<Seat> seats = seatRepository.findByCafeId(cafeId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Seat seat : seats) {
            List<Ai_table> logs = logsBySeat.getOrDefault(seat.getId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(Ai_table::getCreatedAt))
                    .toList();

            // active 시작 ~ available/cleaning 전환까지 세션 계산 (5분 병합)
            List<Long> sessionMinutes = new ArrayList<>();
            LocalDateTime sessionStart = null;
            LocalDateTime lastActiveEnd = null;
            final long MERGE_SECONDS = 5 * 60;

            for (Ai_table log : logs) {
                String s = log.getStatus();
                if ("active".equals(s)) {
                    if (sessionStart == null) {
                        sessionStart = log.getCreatedAt();
                    } else if (lastActiveEnd != null) {
                        long gap = java.time.Duration.between(lastActiveEnd, log.getCreatedAt()).getSeconds();
                        if (gap > MERGE_SECONDS) {
                            // 새 세션
                            long min = java.time.Duration.between(sessionStart, lastActiveEnd).toMinutes();
                            if (min > 0) sessionMinutes.add(min);
                            sessionStart = log.getCreatedAt();
                        }
                    }
                    lastActiveEnd = null;
                } else if ("available".equals(s) || "cleaning".equals(s)) {
                    if (sessionStart != null) {
                        lastActiveEnd = log.getCreatedAt();
                    }
                }
            }
            // 마지막 세션 처리
            if (sessionStart != null && lastActiveEnd != null) {
                long min = java.time.Duration.between(sessionStart, lastActiveEnd).toMinutes();
                if (min > 0) sessionMinutes.add(min);
            }

            if (sessionMinutes.isEmpty()) continue;

            double avg = sessionMinutes.stream().mapToLong(Long::longValue).average().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("seatName",     seat.getName());
            item.put("floorName",    seat.getFloorName());
            item.put("avgMinutes",   Math.round(avg * 10) / 10.0);
            item.put("sessionCount", sessionMinutes.size());
            item.put("maxMinutes",   sessionMinutes.stream().mapToLong(Long::longValue).max().orElse(0));
            result.add(item);
        }

        result.sort((a, b) -> Double.compare((Double) b.get("avgMinutes"), (Double) a.get("avgMinutes")));
        return ResponseEntity.ok(result);
    }

    // 날씨-점유율 관계 데이터 (산점도용)
    @GetMapping("/weather-relation")
    public ResponseEntity<List<Map<String, Object>>> weatherRelation(
            @RequestParam String cafeName,
            @RequestParam(defaultValue = "60") int days) {

        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);

        Map<String, String> normalize = new HashMap<>();
        normalize.put("Clear","맑음"); normalize.put("Clouds","흐림");
        normalize.put("Rain","비"); normalize.put("Drizzle","비"); normalize.put("Thunderstorm","비"); normalize.put("Squall","비"); normalize.put("Tornado","비");
        normalize.put("Snow","눈");
        normalize.put("Dust","황사"); normalize.put("Sand","황사"); normalize.put("Haze","황사");
        normalize.put("Mist","흐림"); normalize.put("Fog","흐림"); normalize.put("Smoke","흐림"); normalize.put("Ash","흐림");

        Map<String, String> colorMap = new HashMap<>();
        colorMap.put("맑음","#f59e0b"); colorMap.put("흐림","#94a3b8");
        colorMap.put("비","#3b82f6"); colorMap.put("눈","#bae6fd"); colorMap.put("황사","#d97706");

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(
                    cafeName, d.atStartOfDay(), d.atTime(LocalTime.MAX));
            if (logs.isEmpty()) continue;

            Optional<WeatherLog> noonOpt = weatherLogRepository.findByLogDateAndLogHour(d, 12);
            Optional<WeatherLog> anyOpt  = weatherLogRepository.findTopByLogDateOrderByLogHourAsc(d);
            WeatherLog wl = noonOpt.orElse(anyOpt.orElse(null));
            if (wl == null || wl.getTemp() == null) continue;

            long active = logs.stream().filter(l -> "active".equals(l.getStatus())).count();
            double occ  = Math.round(active * 1000.0 / logs.size()) / 10.0;
            String label = normalize.getOrDefault(wl.getWeatherMain(), "흐림");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date",      d.toString());
            item.put("temp",      Math.round(wl.getTemp() * 10) / 10.0);
            item.put("occupancy", occ);
            item.put("weather",   label);
            item.put("color",     colorMap.getOrDefault(label, "#6b7280"));
            item.put("humidity",  wl.getHumidity());
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // 날짜별 이용률 추이 (최근 N일)
    @GetMapping("/daily-trend")
    public ResponseEntity<List<Map<String, Object>>> dailyTrend(
            @RequestParam String cafeName,
            @RequestParam(defaultValue = "30") int days) {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);
        List<Map<String, Object>> result = new ArrayList<>();

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(
                    cafeName, d.atStartOfDay(), d.atTime(LocalTime.MAX));
            long active = logs.stream().filter(l -> "active".equals(l.getStatus())).count();
            double occ = logs.isEmpty() ? 0 : Math.round(active * 1000.0 / logs.size()) / 10.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.toString());
            item.put("occupancy", occ);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // 요일별 평균 점유율
    @GetMapping("/weekday-occupancy")
    public ResponseEntity<List<Map<String, Object>>> weekdayOccupancy(
            @RequestParam String cafeName,
            @RequestParam(defaultValue = "90") int days) {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);

        String[] labels = {"월", "화", "수", "목", "금", "토", "일"};
        Map<Integer, List<Double>> byDow = new LinkedHashMap<>();
        for (int i = 1; i <= 7; i++) byDow.put(i, new ArrayList<>());

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(
                    cafeName, d.atStartOfDay(), d.atTime(LocalTime.MAX));
            if (logs.isEmpty()) continue;
            long active = logs.stream().filter(l -> "active".equals(l.getStatus())).count();
            int dow = d.getDayOfWeek().getValue(); // 1=월 ~ 7=일
            byDow.get(dow).add(active * 100.0 / logs.size());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            List<Double> vals = byDow.get(i);
            double avg = vals.isEmpty() ? 0 : Math.round(vals.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("day", labels[i - 1]);
            item.put("occupancy", avg);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    // 시간대별 평균 점유율
    @GetMapping("/hourly-occupancy")
    public ResponseEntity<List<Map<String, Object>>> hourlyOccupancy(
            @RequestParam String cafeName,
            @RequestParam(defaultValue = "30") int days) {

        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1);

        Map<Integer, List<Double>> byHour = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) byHour.put(h, new ArrayList<>());

        List<Ai_table> logs = aiLogRepository.findByCafeNameAndDateRange(
                cafeName, from.atStartOfDay(), to.atTime(LocalTime.MAX));

        // 시간대별로 그룹핑
        Map<Integer, Long> activeByHour = new LinkedHashMap<>();
        Map<Integer, Long> totalByHour  = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) { activeByHour.put(h, 0L); totalByHour.put(h, 0L); }

        for (Ai_table log : logs) {
            int h = log.getCreatedAt().getHour();
            totalByHour.merge(h, 1L, Long::sum);
            if ("active".equals(log.getStatus())) activeByHour.merge(h, 1L, Long::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            long total = totalByHour.get(h);
            double occ = total == 0 ? 0 : Math.round(activeByHour.get(h) * 1000.0 / total) / 10.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", h + "시");
            item.put("occupancy", occ);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }
}
