package com.cafe.cafe_server.ai;

import com.cafe.cafe_server.cafatable_x_y.Cafe;
import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
import com.cafe.cafe_server.cafatable_x_y.Seat;
import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
import com.cafe.cafe_server.sse.SeatSseEmitterService;
import com.cafe.cafe_server.sse.SeatUpdateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ai_db_save {

    private final SeatRepository       seatRepository;
    private final CafeRepository       cafeRepository;
    private final Ai_db_Repository     aiDetailLogRepository;
    private final ObjectMapper         objectMapper;
    private final SeatSseEmitterService sseEmitterService;

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
            case "order_check"       -> "away";
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

        // AI가 보낸 floorId 기준으로 해당 층 좌석만 조회 (전체 카페 좌석 조회 시 다층 인덱스 혼용 버그 방지)
        Integer floorId = dto.getFloorId() != null ? dto.getFloorId().intValue() : 1;
        List<Seat> cafeSeats = seatRepository.findByCafeIdAndFloorNumber(cafe.getId(), floorId);
        cafeSeats.sort(Comparator.comparing(Seat::getId));

        log.info("📋 카페 [{}] {}층 좌석 수: {}석 | 수신 좌석 업데이트 수: {}",
                cafeName, floorId, cafeSeats.size(), dto.getSeats().size());

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

            // ── 변화 감지 (status / awayTime / personCount) ───────────────────
            String  prevStatus      = seat.getStatus();
            String  prevAwayTime    = seat.getAwayTime() == null ? "" : seat.getAwayTime();
            boolean statusChanged   = !mappedStatus.equals(prevStatus);

            // 새 personCount 계산 (DB 저장 전에 결정)
            int newPersonCount = seat.getPersonCount() != null ? seat.getPersonCount() : 0;
            if (personCount != null) {
                newPersonCount = Math.max(0, Math.min(4, personCount));
            } else {
                switch (legacyStatus) {
                    case "active", "no_drink", "order_check" -> newPersonCount = Math.max(1, newPersonCount);
                    case "available"                          -> newPersonCount = 0;
                    default -> { /* 기존 값 유지 */ }
                }
            }
            if ("cleaning".equals(mappedStatus)) newPersonCount = 0;

            boolean awayTimeChanged   = !awayTime.equals(prevAwayTime);
            boolean personCountChanged = newPersonCount != (seat.getPersonCount() != null ? seat.getPersonCount() : 0);
            boolean anyChanged = statusChanged || awayTimeChanged || personCountChanged;

            // ── 변화가 있을 때만 DB 저장 (불필요한 UPDATE 제거) ──────────────────
            if (anyChanged) {
                seat.setStatus(mappedStatus);
                seat.setAwayTime(awayTime);
                seat.setPersonCount(newPersonCount);
                seatRepository.save(seat);
                savedCount++;
                log.info("✅ [DB 저장] 카페={} | 좌석 {} | {}→{} | awayTime={} | 인원={}",
                        cafeName, seatIndex + 1, prevStatus, mappedStatus,
                        awayTime.isBlank() ? "-" : awayTime, newPersonCount);
            }

            // ── ai_detail_log 이력 기록 (status 변화 시만) ────────────────────
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
                log.info("📝 [이력 저장] 카페={} | 좌석 {} | AI원본={} → DB={}",
                        cafeName, seatIndex + 1, rawAiStatus, mappedStatus);
            }
        }

        // ── 전체 처리 완료 요약 로그 ──────────────────────────────────────────
        log.info("🎉 [AI 처리 완료] 카페={} | 수신={}건 | DB저장={}건 | 상태변화={}건",
                cafeName, dto.getSeats().size(), savedCount, changedCount);

        // ── SSE broadcast: 변화가 있을 때만 push ──────────────────────────────
        if (savedCount > 0) {
            List<SeatUpdateEvent.SeatState> states = new ArrayList<>();
            for (Seat s : cafeSeats) {
                states.add(new SeatUpdateEvent.SeatState(
                        s.getName(),
                        s.getStatus(),
                        s.getAwayTime(),
                        s.getPersonCount() != null ? s.getPersonCount() : 0
                ));
            }
            sseEmitterService.broadcast(cafeName, new SeatUpdateEvent(cafeName, floorId, states));
        }
    }
}
