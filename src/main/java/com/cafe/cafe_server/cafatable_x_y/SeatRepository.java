package com.cafe.cafe_server.cafatable_x_y;

//import com.cafe.cafe_server.cafatable_x_y.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByCafeId(Long cafeId);
    void deleteByCafeId(Long cafeId); // 배치 변경 시 기존 좌석을 초기화하고 재저장할 때 사용
}
