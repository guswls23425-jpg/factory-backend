// 로그인데이터베이스 도면 
package com.cafe.cafe_server.auth;

import com.cafe.cafe_server.cafatable_x_y.Cafe;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userName; // 이름

    @Column(nullable = false)
    private String email; // 이메일(ID)

    @OneToOne(mappedBy = "owner", cascade = CascadeType.ALL)
    private Cafe cafe;

    @Column(nullable = false)
    private String password; // 비밀번호

}
