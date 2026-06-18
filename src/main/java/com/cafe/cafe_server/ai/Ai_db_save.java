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
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class Ai_db_save {

    private final SeatRepository        seatRepository;
    private final CafeRepository        cafeRepository;
    private final Ai_db_Repository      aiDetailLogRepository;
    private final ObjectMapper          objectMapper;
    private final SeatSseEmitterService sseEmitterService;

    // ── AI status → DB 정규화 ──────────────────────────────────────────────────
    private String mapAiStatus(String aiStatus) {
        if (aiStatus == null) return "available";
        return switch (aiStatus.toUpperCase()) {
            case "OCCUPIED", "TABLE_IN_USE", "ACTIVE"               -> "active";
            case "AWAY", "TABLE_AWAY", "NO_DRINK",
                 "LONG_AWAY", "ORDER_CHECK", "NON_PURCHASE",
                 "UNPAID_SUSPECTED"                                   -> "away";
            case "EMPTY", "TABLE_EXIT", "AVAILABLE"                  -> "available";
            case "WARNING", "CLEANING", "TABLE_CLEANING",
                 "LIQUID_SPILL", "NEEDS_CLEANING", "SPILL"           -> "cleaning";
            default -> {
                log.warn("⚠️ 알 수 없는 AI status: [{}] → available 대체", aiStatus);
                yield "available";
            }
        };
    }

    @Transactional
    public void saveAndNotifyAiData(String cafeName, AiUpdateDto dto) {
        if (dto.getSeats() == null || dto.getSeats().isEmpty()) return;

        Optional<Cafe> cafeOpt = cafeRepository.findByName(cafeName);
        if (cafeOpt.isEmpty()) {
            log.warn("⚠️ 카페 [{}]를 찾을 수 없습니다.", cafeName);
            return;
        }
        Cafe cafe = cafeOpt.get();

        int floorId = dto.getFloorId() != null ? dto.getFloorId().intValue() : 1;
        List<Seat> cafeSeats = seatRepository.findByCafeIdAndFloorNumber(cafe.getId(), floorId);
        cafeSeats.sort(Comparator.comparing(Seat::getId));

        log.info("📋 카페 [{}] {}층 좌석 수: {}석 | 수신: {}건",
                cafeName, floorId, cafeSeats.size(), dto.getSeats().size());

        int savedCount = 0, changedCount = 0;

        for (AiUpdateDto.SeatUpdate su : dto.getSeats()) {
            if (su.getSeatNumber() == null || su.getStatus() == null) continue;

            int seatIndex     = su.getSeatNumber() - 1; // 1-based → 0-based
            String rawStatus  = su.getStatus();
            String mappedStatus = mapAiStatus(rawStatus);

            if (seatIndex < 0 || seatIndex >= cafeSeats.size()) {
                log.warn("⚠️ seatNumber={} 범위 초과 (전체 {}석) 스킵", su.getSeatNumber(), cafeSeats.size());
                continue;
            }
            Seat seat = cafeSeats.get(seatIndex);

            log.info("🪑 [{}] {}번 좌석(DB id={}) | AI={} → DB={} | 인원={}",
                    cafeName, su.getSeatNumber(), seat.getId(),
                    rawStatus, mappedStatus, su.getOccupantCount());

            // ── 변화 감지 ─────────────────────────────────────────────────────
            String prevStatus = seat.getStatus();
            boolean statusChanged = !mappedStatus.equals(prevStatus);

            int newPersonCount = seat.getPersonCount() != null ? seat.getPersonCount() : 0;
            if (su.getOccupantCount() != null) {
                newPersonCount = Math.max(0, Math.min(4, su.getOccupantCount()));
            }
            if ("cleaning".equals(mappedStatus) || "available".equals(mappedStatus)) {
                newPersonCount = 0;
            }

            // awayTime: awayDurationSeconds를 분 단위 문자열로 변환
            String awayTime = "";
            if (su.getAwayDurationSeconds() != null && su.getAwayDurationSeconds() > 0) {
                long mins = su.getAwayDurationSeconds() / 60;
                long secs = su.getAwayDurationSeconds() % 60;
                awayTime = mins > 0 ? mins + "분 " + secs + "초" : secs + "초";
            }

            boolean personCountChanged = newPersonCount != (seat.getPersonCount() != null ? seat.getPersonCount() : 0);
            boolean awayTimeChanged    = !awayTime.equals(seat.getAwayTime() == null ? "" : seat.getAwayTime());
            boolean anyChanged = statusChanged || personCountChanged || awayTimeChanged;

            if (anyChanged) {
                seat.setStatus(mappedStatus);
                seat.setAwayTime(awayTime.isBlank() ? null : awayTime);
                seat.setPersonCount(newPersonCount);
                seatRepository.save(seat);
                savedCount++;
                log.info("✅ [DB 저장] 좌석 {} | {}→{} | 인원={} | awayTime={}",
                        su.getSeatNumber(), prevStatus, mappedStatus, newPersonCount, awayTime);
            }

            // ── 상태 변화 시 로그 기록 ────────────────────────────────────────
            if (statusChanged) {
                changedCount++;
                Ai_table logEntity = new Ai_table();
                logEntity.setSeat(seat);
                logEntity.setStatus(mappedStatus);
                logEntity.setRawAiStatus(rawStatus);
                logEntity.setOccupantCount(su.getOccupantCount());
                logEntity.setAwayTime(awayTime.isBlank() ? null : awayTime);
                logEntity.setAwayDurationSeconds(su.getAwayDurationSeconds());
                logEntity.setStatusDurationSeconds(su.getStatusDurationSeconds());
                logEntity.setColorChangeRatio(su.getColorChangeRatio());
                logEntity.setSpillDetectedAt(su.getSpillDetectedAt());

                if (su.getEvents() != null && !su.getEvents().isEmpty()) {
                    try {
                        logEntity.setEvents(objectMapper.writeValueAsString(su.getEvents()));
                    } catch (Exception e) {
                        log.warn("events 직렬화 실패: {}", e.getMessage());
                    }
                }

                try {
                    logEntity.setRawJsonData(objectMapper.writeValueAsString(su));
                } catch (Exception e) {
                    log.error("rawJsonData 직렬화 실패", e);
                }

                aiDetailLogRepository.save(logEntity);
                log.info("📝 [이력 저장] 좌석 {} | {}→{}", su.getSeatNumber(), prevStatus, mappedStatus);
            }
        }

        log.info("🎉 [AI 처리 완료] 카페={} | 수신={}건 | DB저장={}건 | 상태변화={}건",
                cafeName, dto.getSeats().size(), savedCount, changedCount);

        if (savedCount > 0) {
            List<SeatUpdateEvent.SeatState> states = new ArrayList<>();
            for (Seat s : cafeSeats) {
                states.add(new SeatUpdateEvent.SeatState(
                        s.getName(), s.getStatus(),
                        s.getAwayTime(),
                        s.getPersonCount() != null ? s.getPersonCount() : 0));
            }
            sseEmitterService.broadcast(cafeName, new SeatUpdateEvent(cafeName, floorId, states));
        }
    }
}
