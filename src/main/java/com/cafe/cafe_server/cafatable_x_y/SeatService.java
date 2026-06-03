package com.cafe.cafe_server.cafatable_x_y;

//import com.cafe.cafe_server.cafatable_x_y.SeatDto;
//import com.cafe.cafe_server.cafatable_x_y.Cafe;
//import com.cafe.cafe_server.cafatable_x_y.Seat;
//import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
//import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final CafeRepository cafeRepository;
    private final SeatRepository seatRepository;

    // 1. 특정 카페의 실시간 좌석 배치 데이터 조회
    @Transactional(readOnly = true)
    public List<SeatDto> getSeatsByCafeName(String cafeName) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카페입니다."));

        List<Seat> seats = seatRepository.findByCafeId(cafe.getId());

        return seats.stream().map(seat -> {
            SeatDto dto = new SeatDto();
            dto.setId(seat.getId());
            dto.setName(seat.getName());
            dto.setStatus(seat.getStatus());
            dto.setAwayTime(seat.getAwayTime());
            
            
            dto.setPosX(seat.getPosX());
            dto.setPosY(seat.getPosY());
            dto.setPersonCount(seat.getPersonCount() != null ? seat.getPersonCount() : 0);

            return dto;
        }).collect(Collectors.toList());
    }

    // 2. 사장님이 수정된 배치 및 상태를 저장할 때 실행되는 로직
    @Transactional
    public void saveSeats(String cafeName, List<SeatDto> seatDtos) {
        // 카페를 찾거나 없으면 임시 생성 (회원가입 연동 전 테스트용)
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseGet(() -> {
                    Cafe newCafe = new Cafe();
                    newCafe.setName(cafeName);
                    return cafeRepository.save(newCafe);
                });

        // 기존에 저장되어 있던 이 카페의 좌석들을 싹 지우고 새로 덮어쓰기 (단순화 전략)
        seatRepository.deleteByCafeId(cafe.getId());

        for (SeatDto dto : seatDtos) {
            Seat seat = new Seat();
            seat.setName(dto.getName());
            seat.setStatus(dto.getStatus());
            seat.setAwayTime(dto.getAwayTime());
            seat.setPosX(dto.getPosX());
            seat.setPosY(dto.getPosY());
            seat.setPersonCount(dto.getPersonCount() != null ? dto.getPersonCount() : 0);
            seat.setCafe(cafe);
            
            seatRepository.save(seat);
        }
    }
}