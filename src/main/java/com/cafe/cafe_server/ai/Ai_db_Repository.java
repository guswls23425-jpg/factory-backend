package com.cafe.cafe_server.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface Ai_db_Repository extends JpaRepository<Ai_table, Long> {

    // 특정 좌석 전체 로그 (최신순)
    List<Ai_table> findBySeatIdOrderByCreatedAtDesc(Long seatId);

    // 특정 좌석 + 날짜 범위
    List<Ai_table> findBySeatIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long seatId, LocalDateTime from, LocalDateTime to);

    // 카페 전체 좌석 + 날짜 범위 (카페 이름으로 조인)
    @Query("""
            SELECT l FROM Ai_table l
            WHERE l.seat.cafe.name = :cafeName
              AND l.createdAt BETWEEN :from AND :to
            ORDER BY l.createdAt DESC
            """)
    List<Ai_table> findByCafeNameAndDateRange(
            @Param("cafeName") String cafeName,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to);
}