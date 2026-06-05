package com.cafe.cafe_server.cafatable_x_y;

//import com.cafe.cafe_server.cafatable_x_y.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByCafeId(Long cafeId);
    List<Seat> findByCafeIdAndFloorNumber(Long cafeId, Integer floorNumber);
    void deleteByCafeId(Long cafeId);
    void deleteByCafeIdAndFloorNumber(Long cafeId, Integer floorNumber);
}
