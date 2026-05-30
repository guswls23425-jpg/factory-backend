package com.cafe.cafe_server.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface Ai_db_Repository extends JpaRepository<Ai_table, Long> {
    
    // 💡 Next.js로 보낼 때 특정 좌석의 최신 분석 데이터들을 뽑아오기 위한 메서드
    List<Ai_table> findBySeatIdOrderByCreatedAtDesc(Long seatId);
}