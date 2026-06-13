package com.cafe.cafe_server.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatStreamController {

    private final SeatSseEmitterService sseService;

    /**
     * 브라우저가 이 엔드포인트에 연결하면 SSE 채널이 열린다.
     * AI 업데이트가 발생할 때마다 seat-update 이벤트가 push된다.
     *
     * GET /api/seats/stream
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseService.subscribe();
    }
}
