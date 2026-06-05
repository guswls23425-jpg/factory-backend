package com.cafe.cafe_server.cafatable_x_y;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "floor", uniqueConstraints = @UniqueConstraint(columnNames = {"cafe_id", "floor_number"}))
public class Floor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "floor_id")
    private Long id;

    @Column(name = "floor_number", nullable = false)
    private Integer floorNumber; // 1, 2, 3 ...

    @Column(name = "label", nullable = false)
    private String label; // "1층", "2층" ...

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cafe_id", nullable = false)
    private Cafe cafe;
}
