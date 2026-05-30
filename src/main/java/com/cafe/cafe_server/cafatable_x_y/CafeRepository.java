package com.cafe.cafe_server.cafatable_x_y;

//import com.cafe.cafe_server.cafatable_x_y.Cafe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CafeRepository extends JpaRepository<Cafe, Long> {
    Optional<Cafe> findByName(String name);
}