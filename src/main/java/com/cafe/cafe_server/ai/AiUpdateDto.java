package com.cafe.cafe_server.ai;

import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiUpdateDto {
    private Long floorId; 
    
    // 💡 데이터가 가변적일 수 있으므로 구조를 고정하지 않고 Map의 리스트 형태로 유연하게 받음
    private List<Map<String, Object>> seats; 
}