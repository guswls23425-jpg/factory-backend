package com.cafe.cafe_server.ai;

import jakarta.annotation.PostConstruct; // Spring Boot 2.x 버전이라면 javax.annotation.PostConstruct 로 변경
import jakarta.annotation.PreDestroy;  // Spring Boot 2.x 버전이라면 javax.annotation.PreDestroy 로 변경
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
@CrossOrigin(origins = "*")
public class Ai_video {

    // AI로부터 받은 가장 최신 JPEG 이미지를 스레드 안전하게 보관하는 변수
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(null);
    private ServerSocket serverSocket;
    private Thread receiverThread;

    // 스프링부트 실행 시 자동으로 TCP 서버를 백그라운드에서 구동
    @PostConstruct
    public void startTcpServer() {
        receiverThread = new Thread(() -> {
            try {
                // 파이썬 코드의 DEFAULT_PORT인 5001번을 엽니다.
                serverSocket = new ServerSocket(5001);
                System.out.println("[TCP Server] AI 영상 수신용 TCP 포트 5001번 개방");

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        System.out.println("[TCP Server] AI 클라이언트 연결됨: " + socket.getInetAddress());
                        handleAiClient(socket);
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            System.err.println("TCP 소켓 연결 에러: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    // 파이썬 클라이언트가 보낸 패킷(데이터)을 해석하는 메서드
    private void handleAiClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            // 1. 처음엔 JSON 메타데이터 수신
            int metaLength = dis.readInt();
            if (metaLength > 0) {
                byte[] metaBytes = new byte[metaLength];
                dis.readFully(metaBytes); // 정확히 길이만큼 바이트를 읽어옴
                String metadata = new String(metaBytes, StandardCharsets.UTF_8);
                System.out.println("[TCP Server] 메타데이터 수신: " + metadata);
            }

            // 2. 이후부터는 계속해서 JPEG 프레임 수신
            while (true) {
                int frameLength = dis.readInt(); // !I 구조체 (4바이트)
                
                // 프레임 길이가 0이면 전송 종료(End marker)
                if (frameLength == 0) {
                    System.out.println("[TCP Server] AI 스트림 전송 완료 및 종료");
                    break;
                }

                byte[] frameBytes = new byte[frameLength];
                dis.readFully(frameBytes); // 프레임 바이트 읽기
                
                // Next.js로 보낼 최신 프레임을 메모리에 갱신
                latestFrame.set(frameBytes);
            }
        } catch (Exception e) {
            System.out.println("[TCP Server] AI 클라이언트 연결 끊김: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // 스프링부트 종료 시 TCP 소켓 닫기
    @PreDestroy
    public void stopTcpServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (receiverThread != null) {
                receiverThread.interrupt();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =========================================================
    // Next.js (클라이언트) 방향으로 영상을 쏴주는 HTTP 스트리밍 API
    // =========================================================
    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> streamVideo() {
        StreamingResponseBody responseBody = outputStream -> {
            try {
                while (true) {
                    byte[] frame = latestFrame.get();
                    if (frame != null) {
                        // MJPEG 스트림 형식으로 Next.js 브라우저가 영상을 인식하게 함
                        outputStream.write(("--frame\r\n").getBytes());
                        outputStream.write(("Content-Type: image/jpeg\r\n").getBytes());
                        outputStream.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes());
                        outputStream.write(frame);
                        outputStream.write(("\r\n").getBytes());
                        outputStream.flush();
                    }
                    // 프레임을 너무 빨리 읽어서 CPU가 과부하 걸리는 것을 방지 (약 30fps)
                    Thread.sleep(33);
                }
            } catch (Exception e) {
                System.out.println("[HTTP Stream] Next.js 클라이언트 뷰어 연결 종료");
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