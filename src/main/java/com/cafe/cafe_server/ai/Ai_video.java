package com.cafe.cafe_server.ai;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class Ai_video {

    private static final Logger log = LoggerFactory.getLogger(Ai_video.class);

    private final VideoFrameStore videoFrameStore;

    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> streamVideo() {
        log.info("👀 [HTTP Stream] 브라우저 뷰어가 /stream 에 연결했습니다.");

        StreamingResponseBody responseBody = outputStream -> {
            try {
                while (true) {
                    byte[] frame = videoFrameStore.getFrame();
                    if (frame != null) {
                        outputStream.write("--frame\r\n".getBytes());
                        outputStream.write("Content-Type: image/jpeg\r\n".getBytes());
                        outputStream.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());
                        outputStream.write(frame);
                        outputStream.write("\r\n".getBytes());
                        outputStream.flush();
                    }
                    Thread.sleep(33);
                }
            } catch (Exception e) {
                log.info("👋 [HTTP Stream] 브라우저 뷰어가 연결을 종료했습니다.");
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "multipart/x-mixed-replace; boundary=frame")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .body(responseBody);
    }
}
