package com.cafe.cafe_server.kakao;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "kakao_notification_setting")
@Getter @Setter
public class KakaoNotificationSetting {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cafe_name", nullable = false, unique = true)
    private String cafeName;

    @Column(name = "cleaning_alert", nullable = false)
    private boolean cleaningAlert = true;

    @Column(name = "unpaid_alert", nullable = false)
    private boolean unpaidAlert = true;
}
