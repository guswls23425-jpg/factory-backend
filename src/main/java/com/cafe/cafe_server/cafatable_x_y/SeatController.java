package com.cafe.cafe_server.cafatable_x_y;

//import com.cafe.cafe_server.cafatable_x_y.SeatDto;
//import com.cafe.cafe_server.cafatable_x_y.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000") // Next.js 서버의 접근을 허용 (CORS 해결)
public class SeatController {

    private final SeatService seatService;

    // 📢 손님 모드 & 사장님 화면 켤 때: 특정 카페의 좌석 정보 가져오기
    @GetMapping("/search")
    public ResponseEntity<List<SeatDto>> getCafeSeats(@RequestParam("cafeName") String cafeName) {
        try {
            List<SeatDto> seats = seatService.getSeatsByCafeName(cafeName);
            return ResponseEntity.ok(seats);
        } catch (IllegalArgumentException e) {
            // 👇 404 에러를 던지는 대신, 텅 빈 리스트를 정상(200 OK) 응답으로 보냄!
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
    }

    // 📢 사장님 모드에서 [변경사항 저장]을 누를 때: 배치 데이터 저장하기
    // URL 예시: POST http://localhost:8080/api/seats/save
    @PostMapping("/save")
    public ResponseEntity<?> saveCafeSeats(
            @RequestParam("cafeName") String cafeName, // 👇 여기도 동일하게 추가!
            @RequestBody List<SeatDto> seatDtos) {
        
        seatService.saveSeats(cafeName, seatDtos);
        return ResponseEntity.ok(Map.of("message", "배치 정보가 안전하게 데이터베이스에 저장되었습니다."));
    }
}