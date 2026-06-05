package com.cafe.cafe_server.cafatable_x_y;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final CafeRepository   cafeRepository;
    private final SeatRepository   seatRepository;
    private final FloorRepository  floorRepository;

    // ── 카페 조회 or 생성 ─────────────────────────────────────────────────────
    private Cafe getOrCreateCafe(String cafeName) {
        return cafeRepository.findByName(cafeName).orElseGet(() -> {
            Cafe c = new Cafe();
            c.setName(cafeName);
            return cafeRepository.save(c);
        });
    }

    // ── 층별 배치 조회 (Floor 테이블 기반) ────────────────────────────────────
    @Transactional(readOnly = true)
    public List<FloorDto> getFloorsByCafeName(String cafeName) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카페입니다."));

        List<Floor> floors = floorRepository.findByCafeIdOrderByFloorNumberAsc(cafe.getId());

        // Floor 테이블에 아무것도 없으면 1층을 기본으로 반환
        if (floors.isEmpty()) {
            FloorDto def = new FloorDto();
            def.setFloorNumber(1);
            def.setLabel("1층");
            def.setSeats(new ArrayList<>());
            return List.of(def);
        }

        return floors.stream().map(floor -> {
            FloorDto fd = new FloorDto();
            fd.setFloorNumber(floor.getFloorNumber());
            fd.setLabel(floor.getLabel());

            List<SeatDto> seats = seatRepository
                    .findByCafeIdAndFloorNumber(cafe.getId(), floor.getFloorNumber())
                    .stream().map(this::toDto).collect(Collectors.toList());
            fd.setSeats(seats);
            return fd;
        }).collect(Collectors.toList());
    }

    // ── 층별 배치 전체 저장 ────────────────────────────────────────────────────
    @Transactional
    public void saveFloors(String cafeName, List<FloorDto> floorDtos) {
        Cafe cafe = getOrCreateCafe(cafeName);

        // 현재 DB에 있는 층 번호 목록
        List<Integer> existingFloorNumbers = floorRepository
                .findByCafeIdOrderByFloorNumberAsc(cafe.getId())
                .stream().map(Floor::getFloorNumber).collect(Collectors.toList());

        // 요청에 포함된 층 번호 목록
        List<Integer> incomingFloorNumbers = floorDtos.stream()
                .map(FloorDto::getFloorNumber).collect(Collectors.toList());

        // 삭제된 층 처리: DB에는 있지만 요청에 없는 층 → Floor + Seat 삭제
        for (Integer oldFloor : existingFloorNumbers) {
            if (!incomingFloorNumbers.contains(oldFloor)) {
                seatRepository.deleteByCafeIdAndFloorNumber(cafe.getId(), oldFloor);
                floorRepository.findByCafeIdAndFloorNumber(cafe.getId(), oldFloor)
                        .ifPresent(floorRepository::delete);
            }
        }

        // 각 층 저장
        for (FloorDto dto : floorDtos) {
            int floorNumber = dto.getFloorNumber() != null ? dto.getFloorNumber() : 1;
            String label    = dto.getLabel()       != null ? dto.getLabel()       : floorNumber + "층";

            // Floor 행 upsert
            Floor floor = floorRepository
                    .findByCafeIdAndFloorNumber(cafe.getId(), floorNumber)
                    .orElseGet(() -> {
                        Floor f = new Floor();
                        f.setCafe(cafe);
                        f.setFloorNumber(floorNumber);
                        return f;
                    });
            floor.setLabel(label);
            floorRepository.save(floor);

            // 이 층의 좌석 전체 교체
            seatRepository.deleteByCafeIdAndFloorNumber(cafe.getId(), floorNumber);
            if (dto.getSeats() != null) {
                for (SeatDto seatDto : dto.getSeats()) {
                    Seat seat = new Seat();
                    seat.setName(seatDto.getName());
                    seat.setStatus(seatDto.getStatus() != null ? seatDto.getStatus() : "available");
                    seat.setAwayTime(seatDto.getAwayTime());
                    seat.setPosX(seatDto.getPosX());
                    seat.setPosY(seatDto.getPosY());
                    seat.setPersonCount(seatDto.getPersonCount() != null ? seatDto.getPersonCount() : 0);
                    seat.setFloorNumber(floorNumber);
                    seat.setCafe(cafe);
                    seatRepository.save(seat);
                }
            }
        }
    }

    // ── 기존 단일 층 조회 (하위 호환) ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SeatDto> getSeatsByCafeName(String cafeName) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카페입니다."));
        return seatRepository.findByCafeId(cafe.getId()).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    // ── 기존 단일 층 저장 (하위 호환) ─────────────────────────────────────────
    @Transactional
    public void saveSeats(String cafeName, List<SeatDto> seatDtos) {
        Cafe cafe = getOrCreateCafe(cafeName);
        seatRepository.deleteByCafeId(cafe.getId());

        // Floor 테이블에도 1층 등록 (없으면)
        floorRepository.findByCafeIdAndFloorNumber(cafe.getId(), 1).orElseGet(() -> {
            Floor f = new Floor();
            f.setCafe(cafe);
            f.setFloorNumber(1);
            f.setLabel("1층");
            return floorRepository.save(f);
        });

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

    // ── 공통 변환 ─────────────────────────────────────────────────────────────
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
