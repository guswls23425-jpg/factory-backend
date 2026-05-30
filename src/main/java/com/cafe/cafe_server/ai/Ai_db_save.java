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

    @Transactional
    public void saveAndNotifyAiData(AiUpdateDto dto) {
        if (dto.getSeats() == null) return;

        for (Map<String, Object> seatMap : dto.getSeats()) {
            // 1. 가변 Map 데이터에서 필수 필드 추출
            if (!seatMap.containsKey("seatId") || !seatMap.containsKey("status")) continue;
            
            Long seatId = Long.valueOf(seatMap.get("seatId").toString());
            String status = seatMap.get("status").toString().toLowerCase();
            String awayTime = seatMap.containsKey("awayTime") ? seatMap.get("awayTime").toString() : "";

            Optional<Seat> seatOpt = seatRepository.findById(seatId);
            if (seatOpt.isPresent()) {
                Seat seat = seatOpt.get();

                // 2. 실시간 좌석 현황판 동기화를 위해 기존 Seat 상태 변경
                seat.setStatus(status);
                seat.setAwayTime(awayTime);
                seatRepository.save(seat);

                // 3. 통계용 로그 기록을 위해 AiDetailLog 테이블에 저장
                Ai_table logEntity = new Ai_table();
                logEntity.setSeat(seat);
                logEntity.setStatus(status);
                logEntity.setAwayTime(awayTime);

                try {
                    // 🌟 핵심: 들어온 Map 전체를 JSON 문자열로 압축해서 가변 컬럼에 저장!
                    String rawJson = objectMapper.writeValueAsString(seatMap);
                    logEntity.setRawJsonData(rawJson);
                } catch (Exception e) {
                    log.error("JSON 가변 데이터 압축 실패", e);
                }

                aiDetailLogRepository.save(logEntity);
            }
        }
    }
}