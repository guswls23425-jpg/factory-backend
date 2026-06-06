package com.cafe.cafe_server.ai;

import com.cafe.cafe_server.cafatable_x_y.Cafe;
import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
import com.cafe.cafe_server.cafatable_x_y.Seat;
import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ai_db_save {

    private final SeatRepository    seatRepository;
    private final CafeRepository    cafeRepository;
    private final Ai_db_Repository  aiDetailLogRepository;
    private final ObjectMapper      objectMapper;

    // ──────────────────────────────────────────────────────────────────────────
    // AI status 코드 → DB 저장값 변환
    //
    // [AI가 보내는 코드]         [DB 저장값]     [의미]
    //  table_in_use        →   active          사용 중 (원래 규격)
    //  table_away          →   away            자리비움 (원래 규격)
    //  table_exit          →   available       퇴장 (원래 규격)
    //  table_cleaning      →   cleaning        청소 중 (원래 규격)
    //  liquid_spill        →   cleaning        액체 흘림 (원래 규격)
    //  unpaid_suspected    →   away            미결제 의심 (원래 규격)
    //  ----- AI 현재 코드 (추가) -----
    //  active              →   active          사용 중
    //  available           →   available       빈자리
    //  away                →   away            자리비움
    //  no_drink            →   away            음료 없이 착석
    //  long_away           →   away            장시간 자리비움
    //  needs_cleaning      →   cleaning        정리 필요
    //  spill               →   cleaning        음료 쏟음
    //  non_purchase        →   away            미결제 의심
    // ──────────────────────────────────────────────────────────────────────────
    private String mapAiStatus(String aiStatus) {
        if (aiStatus == null) return "available";
        return switch (aiStatus.toLowerCase()) {
            // 원래 규격 (백엔드 기대값)
            case "table_in_use"      -> "active";
            case "table_away"        -> "away";
            case "table_exit"        -> "available";
            case "table_cleaning"    -> "cleaning";
            case "liquid_spill"      -> "cleaning";
            case "unpaid_suspected"  -> "away";
            // AI 현재 코드 (직접 매핑 추가)
            case "active"            -> "active";
            case "available"         -> "available";
            case "away"              -> "away";
            case "no_drink"          -> "away";
            case "long_away"         -> "away";
            case "needs_cleaning"    -> "cleaning";
            case "spill"             -> "cleaning";
            case "non_purchase"      -> "away";
            default -> {
                log.warn("⚠️ 알 수 없는 AI status 코드: [{}] → available 로 대체", aiStatus);
                yield "available";
            }
        };
    }

    @Transactional
    public void saveAndNotifyAiData(String cafeName, AiUpdateDto dto) {
        if (dto.getSeats() == null || dto.getSeats().isEmpty()) return;

        // 카페 조회
        Optional<Cafe> cafeOpt = cafeRepository.findByName(cafeName);
        if (cafeOpt.isEmpty()) {
            log.warn("⚠️ 카페 [{}]를 DB에서 찾을 수 없습니다. 관리자가 배치를 저장했는지 확인하세요.", cafeName);
            return;
        }
        Cafe cafe = cafeOpt.get();

        // 해당 카페의 좌석 목록을 seat_id 오름차순으로 정렬 (AI의 1,2,3... 인덱스 기준)
        List<Seat> cafeSeats = seatRepository.findByCafeId(cafe.getId());
        cafeSeats.sort(Comparator.comparing(Seat::getId));

        log.info("📋 카페 [{}] 좌석 수: {}석 | 수신 좌석 업데이트 수: {}",
                cafeName, cafeSeats.size(), dto.getSeats().size());

        int savedCount   = 0; // 실제 DB 저장된 좌석 수
        int changedCount = 0; // 상태가 변화한 좌석 수

        for (Map<String, Object> seatMap : dto.getSeats()) {
            if (!seatMap.containsKey("seatId") || !seatMap.containsKey("status")) continue;

            // ── AI 필드 추출 ──────────────────────────────────────────────────
            int seatIndex = Integer.parseInt(seatMap.get("seatId").toString()) - 1; // 1-based → 0-based
            String rawAiStatus  = seatMap.get("status").toString();
            String mappedStatus = mapAiStatus(rawAiStatus);
            String awayTime     = seatMap.getOrDefault("awayTime",    "").toString();
            String statusLabel  = seatMap.getOrDefault("statusLabel", "").toString();
            String legacyStatus = seatMap.getOrDefault("legacyStatus","").toString();
            Integer personCount = seatMap.containsKey("personCount")
                    ? Integer.valueOf(seatMap.get("personCount").toString()) : null;
            Integer statusDuration = seatMap.containsKey("statusDuration")
                    ? Integer.valueOf(seatMap.get("statusDuration").toString()) : null;

            // ── 인덱스로 좌석 조회 ────────────────────────────────────────────
            if (seatIndex < 0 || seatIndex >= cafeSeats.size()) {
                log.warn("⚠️ seatId(인덱스)={} 가 범위를 벗어났습니다. (전체 {}석) 스킵합니다.",
                        seatIndex + 1, cafeSeats.size());
                continue;
            }
            Seat seat = cafeSeats.get(seatIndex);

            log.info("🪑 [{}] {}번째 좌석(DB id={}) | AI원본={} → DB저장={} | awayTime={}",
                    cafeName, seatIndex + 1, seat.getId(), rawAiStatus, mappedStatus, awayTime);

            // ── 상태 변화 감지 ────────────────────────────────────────────────
            String prevStatus = seat.getStatus();
            boolean statusChanged = !mappedStatus.equals(prevStatus);

            // ── Seat 테이블 업데이트 (매번 덮어씀 — 현황판 최신화) ──────────────
            seat.setStatus(mappedStatus);
            seat.setAwayTime(awayTime);

            if (personCount != null) {
                seat.setPersonCount(Math.max(0, Math.min(4, personCount)));
            }
            if ("cleaning".equals(mappedStatus)) {
                seat.setPersonCount(0);
            }
            seatRepository.save(seat);

            // ── DB 저장 성공 로그 ─────────────────────────────────────────────
            log.info("✅ [DB 저장 완료] 카페={} | 좌석 {} (DB id={}) | 상태={} | awayTime={}",
                    cafeName, seatIndex + 1, seat.getId(), mappedStatus, awayTime.isBlank() ? "-" : awayTime);

            // ── ai_detail_log 이력 기록 (상태가 바뀔 때만 INSERT) ──────────────
            if (statusChanged) {
                changedCount++;
                log.info("🔔 [상태 변화] 카페={} | 좌석 {} : [{}] → [{}] → 이력 로그 저장",
                        cafeName, seatIndex + 1, prevStatus, mappedStatus);

                Ai_table logEntity = new Ai_table();
                logEntity.setSeat(seat);
                logEntity.setStatus(mappedStatus);
                logEntity.setRawAiStatus(rawAiStatus);
                logEntity.setStatusLabel(statusLabel);
                logEntity.setLegacyStatus(legacyStatus);
                logEntity.setAwayTime(awayTime);
                logEntity.setStatusDuration(statusDuration);

                try {
                    logEntity.setRawJsonData(objectMapper.writeValueAsString(seatMap));
                } catch (Exception e) {
                    log.error("JSON 직렬화 실패", e);
                }
                aiDetailLogRepository.save(logEntity);
                log.info("📝 [이력 저장 완료] 카페={} | 좌석 {} | AI원본={} → DB={}",
                        cafeName, seatIndex + 1, rawAiStatus, mappedStatus);
            }
            savedCount++;
        }

        // ── 전체 처리 완료 요약 로그 ──────────────────────────────────────────
        log.info("🎉 [AI 데이터 처리 완료] 카페={} | 수신={}건 | DB저장={}건 | 상태변화={}건",
                cafeName, dto.getSeats().size(), savedCount, changedCount);
    }
}
