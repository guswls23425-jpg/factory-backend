package com.cafe.cafe_server.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTcpReceiver {

    private final VideoFrameStore videoFrameStore;
    private final Ai_db_save      aiDbSave;
    private final ObjectMapper    objectMapper;

    private ServerSocket serverSocket;
    private Thread       acceptThread;

    @PostConstruct
    public void start() {
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(5001);
                log.info("===== [TCP] AI 수신 포트 5001 개방 =====");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        log.info("🟢 [TCP] AI 연결: {}", socket.getInetAddress());
                        Thread t = new Thread(() -> handle(socket));
                        t.setDaemon(true);
                        t.start();
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            log.error("❌ [TCP] accept 오류: {}", e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.error("❌ [TCP] 서버 소켓 초기화 실패: {}", e.getMessage());
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @PreDestroy
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            if (acceptThread != null) acceptThread.interrupt();
        } catch (IOException e) {
            log.error("TCP 서버 종료 오류: {}", e.getMessage());
        }
    }

    private void handle(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            // 1. 첫 패킷 — JSON 메타데이터
            int metaLen = dis.readInt();
            if (metaLen > 0) {
                byte[] metaBytes = new byte[metaLen];
                dis.readFully(metaBytes);
                String meta = new String(metaBytes, StandardCharsets.UTF_8);
                log.info("📂 [TCP] 메타데이터: {}", meta);
            }

            // 2. 이후 패킷 — JPEG 또는 JSON 상태 데이터
            int frameCount = 0;
            while (true) {
                int len = dis.readInt();
                if (len == 0) {
                    log.info("🏁 [TCP] AI 스트림 종료 시그널");
                    break;
                }
                if (len < 0 || len > 20_000_000) {
                    throw new IOException("비정상 패킷 크기: " + len);
                }

                byte[] data = new byte[len];
                dis.readFully(data);

                if (isJson(data)) {
                    // JSON 상태 데이터 → DB 저장
                    routeJson(data);
                } else {
                    // JPEG 프레임 → 영상 스트림
                    if (len >= 1024) {
                        videoFrameStore.setFrame(data);
                        frameCount++;
                        if (frameCount == 1) {
                            log.info("📸 [TCP] 첫 유효 프레임 수신 ({} bytes)", len);
                        } else if (frameCount % 100 == 0) {
                            log.info("🔄 [TCP] 프레임 수신 중... {}프레임", frameCount);
                        }
                    } else {
                        log.warn("⚠️ [TCP] 작은 패킷 무시 ({} bytes)", len);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("🔴 [TCP] AI 연결 종료: {}", e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /** 패킷 첫 바이트가 '{' 이면 JSON으로 판단 */
    private boolean isJson(byte[] data) {
        for (byte b : data) {
            if (b == ' ' || b == '\r' || b == '\n') continue;
            return b == '{';
        }
        return false;
    }

    private void routeJson(byte[] data) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            log.info("📡 [TCP] JSON 상태 데이터 수신: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);

            AiUpdateDto dto = objectMapper.readValue(json, AiUpdateDto.class);
            String cafeName = dto.getCafeName();
            if (cafeName == null || cafeName.isBlank()) {
                log.warn("⚠️ [TCP] JSON에 cafeName 없음 — 무시");
                return;
            }
            aiDbSave.saveAndNotifyAiData(cafeName, dto);
        } catch (Exception e) {
            log.error("❌ [TCP] JSON 파싱/저장 오류: {}", e.getMessage());
        }
    }
}
