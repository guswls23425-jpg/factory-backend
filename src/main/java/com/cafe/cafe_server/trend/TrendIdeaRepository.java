package com.cafe.cafe_server.trend;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrendIdeaRepository extends JpaRepository<TrendIdea, Long> {
    List<TrendIdea> findByCafeNameOrderByCreatedAtDesc(String cafeName);
    void deleteByCafeName(String cafeName);
}
