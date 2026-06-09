package com.cafe.cafe_server.ai;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class VideoFrameStore {

    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(null);

    public void setFrame(byte[] frame) {
        latestFrame.set(frame);
    }

    public byte[] getFrame() {
        return latestFrame.get();
    }
}
