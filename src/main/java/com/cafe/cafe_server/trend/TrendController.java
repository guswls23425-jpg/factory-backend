package com.cafe.cafe_server.trend;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trend")
@RequiredArgsConstructor
public class TrendController {

    private final TrendService trendService;

    // 트렌드 아이디어 조회 (없거나 7일 지나면 자동 생성)
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTrend(@RequestParam String cafeName) {
        return ResponseEntity.ok(trendService.getOrGenerate(cafeName));
    }

    // 수동 새로고침
    @PostMapping("/refresh")
    public ResponseEntity<List<Map<String, Object>>> refresh(@RequestParam String cafeName) {
        return ResponseEntity.ok(trendService.refresh(cafeName));
    }
}
