package com.cafe.cafe_server.cafatable_x_y;

//import com.cafe.cafe_server.cafatable_x_y.SeatDto;
//import com.cafe.cafe_server.cafatable_x_y.Cafe;
//import com.cafe.cafe_server.cafatable_x_y.Seat;
//import com.cafe.cafe_server.cafatable_x_y.CafeRepository;
//import com.cafe.cafe_server.cafatable_x_y.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final CafeRepository cafeRepository;
    private final SeatRepository seatRepository;

    // ── 기존: 단일 층 좌석 조회 (하위 호환 유지) ───────────────────────────
    @Transactional(readOnly = true)
    public List<SeatDto> getSeatsByCafeName(String cafeName) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카페입니다."));
        return seatRepository.findByCafeId(cafe.getId()).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    // ── 신규: 층별 좌석 조회 ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<FloorDto> getFloorsByCafeName(String cafeName) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카페입니다."));

        List<Seat> seats = seatRepository.findByCafeId(cafe.getId());

        // floorNumber 기준으로 그룹화
        Map<Integer, List<SeatDto>> grouped = seats.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getFloorNumber() != null ? s.getFloorNumber() : 1,
                        Collectors.mapping(this::toDto, Collectors.toList())
                ));

        // 층 번호 오름차순 정렬, 1층이 없으면 기본 빈 층 추가
        if (grouped.isEmpty()) {
            FloorDto floor1 = new FloorDto();
            floor1.setFloorNumber(1);
            floor1.setLabel("1층");
            floor1.setSeats(new ArrayList<>());
            return List.of(floor1);
        }

        List<FloorDto> floors = new ArrayList<>();
        grouped.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .forEach(entry -> {
                    FloorDto fd = new FloorDto();
                    fd.setFloorNumber(entry.getKey());
                    fd.setLabel(entry.getKey() + "층");
                    fd.setSeats(entry.getValue());
                    floors.add(fd);
                });
        return floors;
    }

    // ── 신규: 층별 좌석 저장 ────────────────────────────────────────────────
    @Transactional
    public void saveFloors(String cafeName, List<FloorDto> floorDtos) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseGet(() -> {
                    Cafe newCafe = new Cafe();
                    newCafe.setName(cafeName);
                    return cafeRepository.save(newCafe);
                });

        seatRepository.deleteByCafeId(cafe.getId());

        for (FloorDto floorDto : floorDtos) {
            int floorNumber = floorDto.getFloorNumber() != null ? floorDto.getFloorNumber() : 1;
            if (floorDto.getSeats() == null) continue;
            for (SeatDto dto : floorDto.getSeats()) {
                Seat seat = new Seat();
                seat.setName(dto.getName());
                seat.setStatus(dto.getStatus() != null ? dto.getStatus() : "available");
                seat.setAwayTime(dto.getAwayTime());
                seat.setPosX(dto.getPosX());
                seat.setPosY(dto.getPosY());
                seat.setPersonCount(dto.getPersonCount() != null ? dto.getPersonCount() : 0);
                seat.setFloorNumber(floorNumber);
                seat.setCafe(cafe);
                seatRepository.save(seat);
            }
        }
    }

    // ── 기존: 단일 층 저장 (하위 호환) ─────────────────────────────────────
    @Transactional
    public void saveSeats(String cafeName, List<SeatDto> seatDtos) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseGet(() -> {
                    Cafe newCafe = new Cafe();
                    newCafe.setName(cafeName);
                    return cafeRepository.save(newCafe);
                });
        seatRepository.deleteByCafeId(cafe.getId());
        for (SeatDto dto : seatDtos) {
            Seat seat = new Seat();
            seat.setName(dto.getName());
            seat.setStatus(dto.getStatus());
            seat.setAwayTime(dto.getAwayTime());
            seat.setPosX(dto.getPosX());
            seat.setPosY(dto.getPosY());
            seat.setPersonCount(dto.getPersonCount() != null ? dto.getPersonCount() : 0);
            seat.setFloorNumber(dto.getFloorNumber() != null ? dto.getFloorNumber() : 1);
            seat.setCafe(cafe);
            seatRepository.save(seat);
        }
    }

    // ── 공통 변환 ────────────────────────────────────────────────────────────
    private SeatDto toDto(Seat seat) {
        SeatDto dto = new SeatDto();
        dto.setId(seat.getId());
        dto.setName(seat.getName());
        dto.setStatus(seat.getStatus());
        dto.setAwayTime(seat.getAwayTime());
        dto.setPosX(seat.getPosX());
        dto.setPosY(seat.getPosY());
        dto.setPersonCount(seat.getPersonCount() != null ? seat.getPersonCount() : 0);
        dto.setFloorNumber(seat.getFloorNumber() != null ? seat.getFloorNumber() : 1);
        return dto;
    }
}