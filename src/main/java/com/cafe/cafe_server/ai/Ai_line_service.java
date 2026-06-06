package com.cafe.cafe_server.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class Ai_line_service {

    private final Ai_db_save aiService;
    private final Ai_db_Repository aiDetailLogRepository;

    /**
     * 📥 AI 서버가 실시간 JSON 데이터를 보낼 때 받는 통로
     *
     * 호출 예시:
     *   POST /api/ai/receiver?cafeName=스타벅스강남점
     *
     * cafeName 전달 방법 (우선순위 순):
     *   1. URL 쿼리 파라미터: ?cafeName=카페이름  (AI의 AI_SERVER_URL에 붙여서 사용)
     *   2. 요청 Body의 cafeName 필드
     */
    @PostMapping("/receiver")
    public ResponseEntity<?> receiveAiData(
            @RequestParam(value = "cafeName", required = false) String cafeNameParam,
            @RequestBody AiUpdateDto aiUpdateDto) {

        // URL 파라미터 우선, 없으면 body에서 가져옴
        String cafeName = (cafeNameParam != null && !cafeNameParam.isBlank())
                ? cafeNameParam
                : aiUpdateDto.getCafeName();

        if (cafeName == null || cafeName.isBlank()) {
            log.warn("⚠️ cafeName이 없습니다. URL에 ?cafeName=카페이름 을 붙여 주세요.");
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "cafeName이 필요합니다. URL 파라미터로 전달하세요: /api/ai/receiver?cafeName=카페이름"
            ));
        }

        log.info("📡 AI 데이터 수신 — 카페: [{}], 좌석 수: {}",
                cafeName, aiUpdateDto.getSeats() == null ? 0 : aiUpdateDto.getSeats().size());

        aiService.saveAndNotifyAiData(cafeName, aiUpdateDto);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "AI 데이터가 DB 저장 및 좌석 갱신에 성공했습니다."
        ));
    }

    /**
     * 📤 Next.js가 특정 좌석의 AI 통계 데이터를 요청할 때
     */
    @GetMapping("/sender")
    public ResponseEntity<?> sendDataToNextJs(@RequestParam("seatId") Long seatId) {
        log.info("💻 Next.js에서 [{}]번 좌석의 AI 로그 요청", seatId);
        List<Ai_table> logs = aiDetailLogRepository.findBySeatIdOrderByCreatedAtDesc(seatId);
        return ResponseEntity.ok(logs);
    }
}
