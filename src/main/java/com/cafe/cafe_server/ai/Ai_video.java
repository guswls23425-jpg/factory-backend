package com.cafe.cafe_server.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/video")
@CrossOrigin(originPatterns = "*")
public class Ai_video {

    private static final Logger log = LoggerFactory.getLogger(Ai_video.class);
    private static final int TCP_PORT = 5001;
    private static final int MAX_FRAME_BYTES = 20_000_000;
    private static final int PACKET_TYPE_JPEG = 1;
    private static final int PACKET_TYPE_STATUS_JSON = 2;

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(null);
    private final ConcurrentHashMap<String, AtomicReference<byte[]>> latestFrames = new ConcurrentHashMap<>();
    private final ObjectProvider<Ai_db_save> aiDbSaveProvider;
    private final ObjectMapper objectMapper;
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private Thread receiverThread;

    public Ai_video(ObjectProvider<Ai_db_save> aiDbSaveProvider, ObjectMapper objectMapper) {
        this.aiDbSaveProvider = aiDbSaveProvider;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void startTcpServer() {
        receiverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                log.info("[TCP Server] AI video receiver opened on port {}", TCP_PORT);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Socket socket = serverSocket.accept();
                        log.info("[TCP Server] AI client connected from {}", socket.getInetAddress());
                        clientExecutor.execute(() -> handleAiClient(socket));
                    } catch (IOException e) {
                        if (serverSocket != null && !serverSocket.isClosed()) {
                            log.error("[TCP Server] Failed to accept socket: {}", e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.error("[TCP Server] Failed to open server socket: {}", e.getMessage());
            }
        }, "ai-video-tcp-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    private void handleAiClient(Socket socket) {
        String sourceId = "unknown-" + socket.getPort();

        try (socket; DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            int metaLength = dis.readInt();
            if (metaLength > 0) {
                byte[] metaBytes = new byte[metaLength];
                dis.readFully(metaBytes);
                String metadata = new String(metaBytes, StandardCharsets.UTF_8);
                sourceId = readSourceId(metadata, sourceId);
                latestFrames.computeIfAbsent(sourceId, ignored -> new AtomicReference<>(null));
                log.info("[TCP Server] Metadata received from {}: {}", sourceId, metadata);
            }

            boolean isFirstFrame = true;
            int frameCounter = 0;

            while (!Thread.currentThread().isInterrupted()) {
                int packetLength = dis.readInt();

                if (packetLength == 0) {
                    log.info("[TCP Server] {} sent stream end marker", sourceId);
                    break;
                }

                if (packetLength > MAX_FRAME_BYTES || packetLength < 0) {
                    throw new IOException("Invalid packet size blocked: " + packetLength + " bytes");
                }

                byte[] packetBytes = new byte[packetLength];
                dis.readFully(packetBytes);
                if (packetBytes.length == 0) {
                    continue;
                }

                int packetType = Byte.toUnsignedInt(packetBytes[0]);
                byte[] payloadBytes = java.util.Arrays.copyOfRange(packetBytes, 1, packetBytes.length);

                if (packetType == PACKET_TYPE_JPEG) {
                    latestFrame.set(payloadBytes);
                    latestFrames.computeIfAbsent(sourceId, ignored -> new AtomicReference<>(null)).set(payloadBytes);

                    if (isFirstFrame) {
                        log.info("[TCP Server] First {} video frame received: {} bytes", sourceId, payloadBytes.length);
                        isFirstFrame = false;
                    }

                    frameCounter++;
                    if (frameCounter % 100 == 0) {
                        log.info("[TCP Server] Receiving {} video frames: {} frames total", sourceId, frameCounter);
                    }
                } else if (packetType == PACKET_TYPE_STATUS_JSON) {
                    handleStatusJson(payloadBytes, sourceId);
                } else {
                    log.warn("[TCP Server] Unknown AI packet type ignored from {}: {}", sourceId, packetType);
                }
            }
        } catch (Exception e) {
            log.warn("[TCP Server] AI client {} disconnected: {}", sourceId, e.getMessage());
        }
    }

    private String readSourceId(String metadata, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(metadata);
            String source = root.path("source").asText("").trim();
            return source.isEmpty() ? fallback : source;
        } catch (Exception e) {
            log.warn("[TCP Server] Failed to parse AI metadata: {}", e.getMessage());
            return fallback;
        }
    }

    private void handleStatusJson(byte[] payloadBytes, String sourceId) {
        try {
            AiUpdateDto dto = objectMapper.readValue(payloadBytes, AiUpdateDto.class);
            Ai_db_save aiDbSave = aiDbSaveProvider.getIfAvailable();
            if (aiDbSave == null) {
                log.warn("[TCP Server] AI status JSON received from {} but DB save service is unavailable", sourceId);
                return;
            }
            aiDbSave.saveAndNotifyAiData(dto.getCafeName(), dto);
            log.info("[TCP Server] AI status JSON saved from {}: {} seats", sourceId,
                    dto.getSeats() == null ? 0 : dto.getSeats().size());
        } catch (Exception e) {
            log.warn("[TCP Server] Failed to handle AI status JSON from {}: {}", sourceId, e.getMessage());
        }
    }

    @PreDestroy
    public void stopTcpServer() {
        try {
            log.info("[TCP Server] Closing AI video receiver");
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (receiverThread != null) {
                receiverThread.interrupt();
            }
            clientExecutor.shutdownNow();
        } catch (IOException e) {
            log.error("[TCP Server] Error while closing server: {}", e.getMessage());
        }
    }

    @GetMapping("/sources")
    public Set<String> videoSources() {
        return latestFrames.keySet();
    }

    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> streamVideo(
            @RequestParam(required = false) String sourceId) {
        String requestedSource = sourceId == null ? "" : sourceId.trim();
        log.info("[HTTP Stream] Browser connected to /api/video/stream sourceId={}", requestedSource);

        StreamingResponseBody responseBody = outputStream -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    byte[] frame;
                    if (requestedSource.isEmpty()) {
                        frame = latestFrame.get();
                    } else {
                        AtomicReference<byte[]> sourceFrame = latestFrames.get(requestedSource);
                        frame = sourceFrame == null ? null : sourceFrame.get();
                    }

                    if (frame != null) {
                        outputStream.write("--frame\r\n".getBytes(StandardCharsets.UTF_8));
                        outputStream.write("Content-Type: image/jpeg\r\n".getBytes(StandardCharsets.UTF_8));
                        outputStream.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        outputStream.write(frame);
                        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    }
                    Thread.sleep(33);
                }
            } catch (Exception e) {
                log.info("[HTTP Stream] Browser disconnected from sourceId={}", requestedSource);
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
