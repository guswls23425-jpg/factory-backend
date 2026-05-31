package com.cafe.cafe_server.ai;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/video")
@CrossOrigin(originPatterns = "*") // 기존 origins = "*" 에서 발생하던 CORS 에러 방지용 패턴으로 수정
public class Ai_video {

    // 💡 표준 로깅 라이브러리 설정
    private static final Logger log = LoggerFactory.getLogger(Ai_video.class);

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(null);
    private ServerSocket serverSocket;
    private Thread receiverThread;

    @PostConstruct
    public void startTcpServer() {
        receiverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(5001);
                log.info("============= [TCP Server] AI 영상 수신용 TCP 포트 5001번 개방 =============");

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        // 🟢 AI 접속 확인 로그
                        log.info("🟢 [TCP Server] AI 클라이언트 연결 성공! (접속 IP: {})", socket.getInetAddress());
                        handleAiClient(socket);
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            log.error("❌ [TCP Server] 소켓 연결 수락 중 에러 발생: {}", e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.error("❌ [TCP Server] 서버 소켓 초기화 실패: {}", e.getMessage());
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void handleAiClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            
            // 1. 처음엔 JSON 메타데이터 수신
            int metaLength = dis.readInt();
            if (metaLength > 0) {
                byte[] metaBytes = new byte[metaLength];
                dis.readFully(metaBytes);
                String metadata = new String(metaBytes, StandardCharsets.UTF_8);
                // 📂 메타데이터 확인 로그
                log.info("📂 [TCP Server] AI로부터 메타데이터 수신 완료: {}", metadata);
            }

            // 2. 이후부터는 계속해서 JPEG 프레임 수신
            boolean isFirstFrame = true;
            int frameCounter = 0;

            while (true) {
                int frameLength = dis.readInt();
                
                if (frameLength == 0) {
                    log.info("🏁 [TCP Server] AI 클라이언트가 스트림 전송 완료 시그널(End Marker)을 보냈습니다.");
                    break;
                }

                // 아까 발생했던 OutOfMemoryError 방지를 위한 안전장치 (20MB 초과 시 차단)
                if (frameLength > 20_000_000 || frameLength < 0) {
                    throw new IOException("비정상적인 프레임 크기 감지 및 차단: " + frameLength + " bytes");
                }

                byte[] frameBytes = new byte[frameLength];
                dis.readFully(frameBytes);
                latestFrame.set(frameBytes);

                // 📸 프레임 수신 확인 실시간 로그
                if (isFirstFrame) {
                    log.info("📸 [TCP Server] 최초 영상 프레임 수신 성공! (첫 프레임 크기: {} bytes) 이제 실시간 스트리밍이 활성화됩니다.", frameLength);
                    isFirstFrame = false;
                }

                frameCounter++;
                // 너무 많은 로그로 도배되는 것을 막기 위해 100프레임마다 생존 신고 로그 출력 (약 3초마다 1번)
                if (frameCounter % 100 == 0) {
                    log.info("🔄 [TCP Server] 영상 프레임 수신 중... (현재 누적 수신: {} 프레임)", frameCounter);
                }
            }
        } catch (Exception e) {
            // 🔴 AI 끊김 확인 로그
            log.warn("🔴 [TCP Server] AI 클라이언트 연결이 종료되었습니다. (원인: {})", e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    @PreDestroy
    public void stopTcpServer() {
        try {
            log.info("종료 절차 시작: TCP 서버 소켓을 닫습니다.");
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (receiverThread != null) {
                receiverThread.interrupt();
            }
        } catch (IOException e) {
            log.error("TCP 서버 종료 중 에러: {}", e.getMessage());
        }
    }

    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> streamVideo() {
        log.info("👀 [HTTP Stream] Next.js 브라우저 뷰어가 /stream 에 연결을 시도했습니다.");
        
        StreamingResponseBody responseBody = outputStream -> {
            try {
                while (true) {
                    byte[] frame = latestFrame.get();
                    if (frame != null) {
                        outputStream.write(("--frame\r\n").getBytes());
                        outputStream.write(("Content-Type: image/jpeg\r\n").getBytes());
                        outputStream.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());
                        outputStream.write(frame);
                        outputStream.write(("\r\n").getBytes());
                        outputStream.flush();
                    }
                    Thread.sleep(33);
                }
            } catch (Exception e) {
                log.info("👋 [HTTP Stream] Next.js 브라우저 뷰어가 연결을 종료했습니다.");
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