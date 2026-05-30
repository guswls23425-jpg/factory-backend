// DB통신 인터페이스
package com.cafe.cafe_server.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    // 이메일로 사장님 정보를 찾는 마법의 메서드
    Optional<Owner> findByEmail(String email);
}
