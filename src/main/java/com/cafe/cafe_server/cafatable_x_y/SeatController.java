package com.cafe.cafe_server.cafatable_x_y;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
//@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class SeatController {

    private final SeatService seatService;

    // 📢 손님 모드 & 사장님 화면 켤 때: 특정 카페의 좌석 정보 가져오기
    @GetMapping("/search")
    public ResponseEntity<List<SeatDto>> getCafeSeats(@RequestParam("cafeName") String cafeName) {
        try {
            List<SeatDto> seats = seatService.getSeatsByCafeName(cafeName);
            return ResponseEntity.ok(seats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    // 📢 사장님 모드에서 [변경사항 저장]을 누를 때: 배치 데이터 저장하기 (기존 단일층)
    @PostMapping("/save")
    public ResponseEntity<?> saveCafeSeats(
            @RequestParam("cafeName") String cafeName,
            @RequestBody List<SeatDto> seatDtos) {
        seatService.saveSeats(cafeName, seatDtos);
        return ResponseEntity.ok(Map.of("message", "배치 정보가 저장되었습니다."));
    }

    // 📢 층별 좌석 전체 조회
    @GetMapping("/floors")
    public ResponseEntity<List<FloorDto>> getFloors(@RequestParam("cafeName") String cafeName) {
        try {
            List<FloorDto> floors = seatService.getFloorsByCafeName(cafeName);
            return ResponseEntity.ok(floors);
        } catch (IllegalArgumentException e) {
            FloorDto floor1 = new FloorDto();
            floor1.setFloorNumber(1);
            floor1.setLabel("1층");
            floor1.setSeats(java.util.Collections.emptyList());
            return ResponseEntity.ok(List.of(floor1));
        }
    }

    // 📢 층별 좌석 전체 저장
    @PostMapping("/floors/save")
    public ResponseEntity<?> saveFloors(
            @RequestParam("cafeName") String cafeName,
            @RequestBody List<FloorDto> floorDtos) {
        seatService.saveFloors(cafeName, floorDtos);
        return ResponseEntity.ok(Map.of("message", "층별 배치 정보가 저장되었습니다."));
    }
}
