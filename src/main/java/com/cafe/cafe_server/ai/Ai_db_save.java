package com.cafe.cafe_server.ai;

import com.cafe.cafe_server.cafatable_x_y.Seat;
import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ai_db_save {

    // 🌟 [여기에 입력!] application.yml의 ai-server.url 값을 이 변수에 주입하겠다는 뜻일세.
    //@Value("${ai-server.url}")
    //private String aiServerUrl;

    private final SeatRepository seatRepository;
    private final Ai_db_Repository aiDetailLogRepository;
    private final ObjectMapper objectMapper; // Map을 JSON 문자열로 변환해 주는 도구

    // ──────────────────────────────────────────────────────────────────
    // AI가 보내는 status 코드 → 프론트/DB에서 사용하는 status 코드로 변환
    //
    // AI 코드(status_code)       →  DB 저장값
    //   table_in_use             →  active
    //   table_away               →  away
    //   table_exit               →  available
    //   table_cleaning           →  cleaning
    //   liquid_spill             →  cleaning   (액체 흘림 = 청소 필요)
    //   unpaid_suspected         →  away       (미결제 의심 = 자리비움으로 처리)
    // ──────────────────────────────────────────────────────────────────
    private String mapAiStatus(String aiStatus) {
        if (aiStatus == null) return "available";
        return switch (aiStatus.toLowerCase()) {
            case "table_in_use"      -> "active";
            case "table_away"        -> "away";
            case "table_exit"        -> "available";
            case "table_cleaning"    -> "cleaning";
            case "liquid_spill"      -> "cleaning";    // 청소 필요로 처리
            case "unpaid_suspected"  -> "away";        // 자리비움으로 처리
            default -> {
                log.warn("⚠️ 알 수 없는 AI status 코드: [{}] → available 로 대체", aiStatus);
                yield "available";
            }
        };
    }

    @Transactional
    public void saveAndNotifyAiData(AiUpdateDto dto) {
        if (dto.getSeats() == null) return;

        for (Map<String, Object> seatMap : dto.getSeats()) {
            if (!seatMap.containsKey("seatId") || !seatMap.containsKey("status")) continue;

            // ── 1. AI 필드 추출 ──────────────────────────────────────────
            Long   seatId          = Long.valueOf(seatMap.get("seatId").toString());
            String rawAiStatus     = seatMap.get("status").toString();          // AI 원본 코드
            String mappedStatus    = mapAiStatus(rawAiStatus);                  // DB 저장용 변환값
            String awayTime        = seatMap.getOrDefault("awayTime",   "").toString();
            String statusLabel     = seatMap.getOrDefault("statusLabel","").toString(); // 한글 라벨
            String legacyStatus    = seatMap.getOrDefault("legacyStatus","").toString(); // AI 내부 원본
            Integer personCount    = seatMap.containsKey("personCount")
                    ? Integer.valueOf(seatMap.get("personCount").toString()) : null;
            Integer statusDuration = seatMap.containsKey("statusDuration")
                    ? Integer.valueOf(seatMap.get("statusDuration").toString()) : null;

            log.info("🪑 seatId={} | AI원본={} → DB저장={} | 라벨={} | awayTime={}",
                    seatId, rawAiStatus, mappedStatus, statusLabel, awayTime);

            // ── 2. Seat 테이블 업데이트 (실시간 현황판 반영) ─────────────
            Optional<Seat> seatOpt = seatRepository.findById(seatId);
            if (seatOpt.isEmpty()) {
                log.warn("⚠️ seatId={} 에 해당하는 좌석이 DB에 없습니다. 스킵합니다.", seatId);
                continue;
            }

            Seat seat = seatOpt.get();
            seat.setStatus(mappedStatus);
            seat.setAwayTime(awayTime);

            if (personCount != null) {
                seat.setPersonCount(Math.max(0, Math.min(4, personCount)));
            }
            // cleaning 상태 진입 시 사람 수 자동 초기화
            if ("cleaning".equals(mappedStatus)) {
                seat.setPersonCount(0);
            }
            seatRepository.save(seat);

            // ── 3. ai_detail_log 테이블에 이력 기록 ──────────────────────
            Ai_table logEntity = new Ai_table();
            logEntity.setSeat(seat);
            logEntity.setStatus(mappedStatus);
            logEntity.setRawAiStatus(rawAiStatus);       // AI 원본 코드 보존
            logEntity.setStatusLabel(statusLabel);        // 한글 라벨
            logEntity.setLegacyStatus(legacyStatus);      // AI 내부 원본
            logEntity.setAwayTime(awayTime);
            logEntity.setStatusDuration(statusDuration);

            try {
                logEntity.setRawJsonData(objectMapper.writeValueAsString(seatMap));
            } catch (Exception e) {
                log.error("JSON 직렬화 실패", e);
            }

            aiDetailLogRepository.save(logEntity);
        }
    }
}