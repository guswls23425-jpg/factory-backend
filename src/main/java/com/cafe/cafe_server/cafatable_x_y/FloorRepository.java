package com.cafe.cafe_server.cafatable_x_y;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {

    List<Floor> findByCafeIdOrderByFloorNumberAsc(Long cafeId);

    Optional<Floor> findByCafeIdAndFloorNumber(Long cafeId, Integer floorNumber);

    void deleteByCafeId(Long cafeId);
}
