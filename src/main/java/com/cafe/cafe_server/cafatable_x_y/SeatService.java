package com.cafe.cafe_server.cafatable_x_y;

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

    private final CafeRepository  cafeRepository;
    private final SeatRepository  seatRepository;
    private final FloorRepository floorRepository;

    // ── 카페 조회 or 생성 ─────────────────────────────────────────────────────
    private Cafe getOrCreateCafe(String cafeName) {
        return cafeRepository.findByName(cafeName).orElseGet(() -> {
            Cafe c = new Cafe();
            c.setName(cafeName);
            return cafeRepository.save(c);
        });
    }

    // ── 층 번호 추출 헬퍼 (floorNumber 우선, 없으면 floorName에서 파싱) ─────────
    private int resolveFloorNumber(SeatDto dto) {
        if (dto.getFloorNumber() != null) return dto.getFloorNumber();
        if (dto.getFloorName() != null) {
            try { return Integer.parseInt(dto.getFloorName().replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    private int resolveFloorNumber(Seat seat) {
        if (seat.getFloorNumber() != null) return seat.getFloorNumber();
        if (seat.getFloorName() != null) {
            try { return Integer.parseInt(seat.getFloorName().replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    // ── 층별 배치 조회 (Floor 테이블 기반) ────────────────────────────────────
    @Transactional(readOnly = true)
    public List<FloorDto> getFloorsByCafeName(String cafeName) {
        Cafe cafe = cafeRepository.findByName(cafeName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카페입니다."));

        List<Floor> floors = floorRepository.findByCafeIdOrderByFloorNumberAsc(cafe.getId());

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

        List<Integer> existingFloorNumbers = floorRepository
                .findByCafeIdOrderByFloorNumberAsc(cafe.getId())
                .stream().map(Floor::getFloorNumber).collect(Collectors.toList());

        List<Integer> incomingFloorNumbers = floorDtos.stream()
                .map(FloorDto::getFloorNumber).collect(Collectors.toList());

        // 삭제된 층 정리
        for (Integer oldFloor : existingFloorNumbers) {
            if (!incomingFloorNumbers.contains(oldFloor)) {
                seatRepository.deleteByCafeIdAndFloorNumber(cafe.getId(), oldFloor);
                floorRepository.findByCafeIdAndFloorNumber(cafe.getId(), oldFloor)
                        .ifPresent(floorRepository::delete);
            }
        }

        for (FloorDto dto : floorDtos) {
            int floorNumber = dto.getFloorNumber() != null ? dto.getFloorNumber() : 1;
            String label    = dto.getLabel()       != null ? dto.getLabel()       : floorNumber + "층";

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

            // ── DELETE+INSERT 대신 이름 기준 UPSERT → seat_id 보존, AI 상태 유지 ──
            List<Long> keptIds = new ArrayList<>();
            if (dto.getSeats() != null) {
                for (SeatDto seatDto : dto.getSeats()) {
                    Seat seat = seatRepository
                            .findByCafeIdAndFloorNumberAndName(cafe.getId(), floorNumber, seatDto.getName())
                            .orElseGet(() -> {
                                // 신규 좌석만 새로 생성 (status 기본값 "available")
                                Seat s = new Seat();
                                s.setCafe(cafe);
                                s.setStatus("available");
                                return s;
                            });
                    // 위치·이름·층 정보만 덮어씀 — AI가 관리하는 status/awayTime/personCount는 건드리지 않음
                    seat.setName(seatDto.getName());
                    seat.setPosX(seatDto.getPosX());
                    seat.setPosY(seatDto.getPosY());
                    seat.setFloorNumber(floorNumber);
                    seat.setFloorName(label);
                    seat.setCafe(cafe);
                    Seat saved = seatRepository.save(seat);
                    keptIds.add(saved.getId());
                }
            }
            // 이번 저장에 포함되지 않은 좌석(삭제된 테이블)만 제거
            if (!keptIds.isEmpty()) {
                seatRepository.deleteByIdNotInAndCafeIdAndFloorNumber(keptIds, cafe.getId(), floorNumber);
            } else {
                seatRepository.deleteByCafeIdAndFloorNumber(cafe.getId(), floorNumber);
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

        // Floor 테이블에 1층 등록 (없으면)
        floorRepository.findByCafeIdAndFloorNumber(cafe.getId(), 1).orElseGet(() -> {
            Floor f = new Floor();
            f.setCafe(cafe);
            f.setFloorNumber(1);
            f.setLabel("1층");
            return floorRepository.save(f);
        });

        for (SeatDto dto : seatDtos) {
            int floorNumber = resolveFloorNumber(dto);
            String floorLabel = floorNumber + "층";

            // 새 층이 있으면 Floor 테이블에 등록
            floorRepository.findByCafeIdAndFloorNumber(cafe.getId(), floorNumber).orElseGet(() -> {
                Floor f = new Floor();
                f.setCafe(cafe);
                f.setFloorNumber(floorNumber);
                f.setLabel(floorLabel);
                return floorRepository.save(f);
            });

            seatRepository.save(fromDto(dto, cafe, floorNumber, floorLabel));
        }
    }

    // ── 공통 변환: Seat → SeatDto ─────────────────────────────────────────────
    private SeatDto toDto(Seat seat) {
        SeatDto dto = new SeatDto();
        dto.setId(seat.getId());
        dto.setName(seat.getName());
        dto.setStatus(seat.getStatus());
        dto.setAwayTime(seat.getAwayTime());
        dto.setPosX(seat.getPosX());
        dto.setPosY(seat.getPosY());
        dto.setPersonCount(seat.getPersonCount() != null ? seat.getPersonCount() : 0);
        int fn = resolveFloorNumber(seat);
        dto.setFloorNumber(fn);
        dto.setFloorName(seat.getFloorName() != null ? seat.getFloorName() : fn + "층");
        return dto;
    }

    // ── 공통 변환: SeatDto → Seat ─────────────────────────────────────────────
    private Seat fromDto(SeatDto dto, Cafe cafe, int floorNumber, String floorLabel) {
        Seat seat = new Seat();
        seat.setName(dto.getName());
        seat.setStatus(dto.getStatus() != null ? dto.getStatus() : "available");
        seat.setAwayTime(dto.getAwayTime());
        seat.setPosX(dto.getPosX());
        seat.setPosY(dto.getPosY());
        seat.setPersonCount(dto.getPersonCount() != null ? dto.getPersonCount() : 0);
        seat.setFloorNumber(floorNumber);
        seat.setFloorName(floorLabel);
        seat.setCafe(cafe);
        return seat;
    }
}
