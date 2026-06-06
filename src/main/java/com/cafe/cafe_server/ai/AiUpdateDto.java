package com.cafe.cafe_server.ai;

import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiUpdateDto {
    private Long floorId;
    private String cafeName;  // 카페 식별용 (URL 파라미터로도 수신 가능)
    private List<Map<String, Object>> seats;
}
