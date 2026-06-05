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
     * 📥 통로 1: AI 서버가 실시간 JSON 데이터를 보낼 때 받는 통로 (AI -> Spring)
     */
    @PostMapping("/receiver")
    public ResponseEntity<?> receiveAiData(@RequestBody AiUpdateDto aiUpdateDto) {
        log.info("📡 AI 서버로부터 실시간 스트리밍 데이터 도달함.");
        
        // 데이터 가공 및 DB 저장 서비스 호출
        aiService.saveAndNotifyAiData(aiUpdateDto);
        
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "AI 데이터가 DB 저장 및 좌석 갱신에 성공했습니다."
        ));
    }

    /**
     * 📤 통로 2: Next.js가 특정 좌석의 가변 통계 데이터를 원할 때 전송해 주는 통로 (Spring -> Next.js)
     */
    @GetMapping("/sender")
    public ResponseEntity<?> sendDataToNextJs(@RequestParam("seatId") Long seatId) {
        log.info("💻 Next.js 화면에서 [{}]번 좌석의 AI 가변 상세 로그를 요청함.", seatId);
        
        // DB에서 해당 좌석의 최신 AI 로그 긁어오기
        List<Ai_table> logs = aiDetailLogRepository.findBySeatIdOrderByCreatedAtDesc(seatId);
        
        // Next.js로 전송
        return ResponseEntity.ok(logs);
    }
}