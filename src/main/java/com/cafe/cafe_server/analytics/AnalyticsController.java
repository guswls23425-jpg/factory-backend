package com.cafe.cafe_server.analytics;

import com.cafe.cafe_server.ai.Ai_db_Repository;
import com.cafe.cafe_server.ai.Ai_table;
import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
import com.cafe.cafe_server.cafatable_x_y.Seat;
import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
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
}
