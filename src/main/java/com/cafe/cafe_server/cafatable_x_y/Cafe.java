package com.cafe.cafe_server.cafatable_x_y;

import java.util.ArrayList;

import com.cafe.cafe_server.auth.Owner;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
//import java.util.ArrayList; // 👈 잊지 말고 임포트!
import java.util.List;      // 👈 잊지 말고 임포트!

@Entity
@Getter
@Setter
@Table(name = "cafe")
public class Cafe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cafe_id")
    private Long id;

    @Column(name = "cafe_name", unique = true, nullable = false)
    private String name;

    // ✨ [추가/확인] 이 카페의 주인이 누구인지 가리키는 외래키(FK)
    @OneToOne
    @JoinColumn(name = "owner_id")
    private Owner owner;

    // ✨ 👇 바로 이 부분입니다! (이 코드가 있어야 setSeats가 작동합니다)
    @OneToMany(mappedBy = "cafe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();
    
}