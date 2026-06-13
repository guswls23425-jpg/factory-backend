package com.cafe.cafe_server.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 연결된 SSE 클라이언트를 관리하고 AI 업데이트를 push하는 서비스.
 *
 * 클라이언트(브라우저)마다 고유 ID를 부여해 ConcurrentHashMap으로 관리한다.
 * AI가 DB를 업데이트할 때마다 broadcast()가 호출되어 모든 연결에 push된다.
 */
@Slf4j
@Service
public class SeatSseEmitterService {

    // emitterId → SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(0);

    /** 새 클라이언트가 /api/seats/stream 에 연결할 때 호출 */
    public SseEmitter subscribe() {
        long id = idGen.incrementAndGet();
        // timeout=0 → 서버가 명시적으로 complete() 할 때까지 연결 유지
        SseEmitter emitter = new SseEmitter(0L);

        emitters.put(id, emitter);
        log.info("✅ [SSE] 클라이언트 연결 (id={}, 현재 {}명)", id, emitters.size());

        // 연결이 끊기면 자동 제거
        Runnable cleanup = () -> {
            emitters.remove(id);
            log.info("🔌 [SSE] 클라이언트 해제 (id={}, 남은 {}명)", id, emitters.size());
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 연결 즉시 heartbeat — nginx 등 프록시의 버퍼링 방지
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * AI 업데이트가 발생할 때 모든 연결된 클라이언트에 push.
     * @param cafeName 카페명 (클라이언트가 자신의 카페 데이터인지 필터링)
     * @param payload  JSON 직렬화된 좌석 변화 데이터
     */
    public void broadcast(String cafeName, Object payload) {
        if (emitters.isEmpty()) return;

        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("seat-update")
                        .data(payload));
            } catch (IOException e) {
                emitters.remove(id);
                log.warn("⚠️ [SSE] 전송 실패로 연결 제거 (id={})", id);
            }
        });

        log.info("📡 [SSE] broadcast → {}명 | 카페={}", emitters.size(), cafeName);
    }
}
