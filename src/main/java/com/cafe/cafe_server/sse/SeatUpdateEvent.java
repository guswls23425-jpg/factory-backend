package com.cafe.cafe_server.sse;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** AI 업데이트 후 SSE로 push되는 이벤트 페이로드 */
@Getter
@AllArgsConstructor
public class SeatUpdateEvent {
    private final String cafeName;
    private final Integer floorId;
    private final List<SeatState> seats;

    @Getter
    @AllArgsConstructor
    public static class SeatState {
        private final String  name;
        private final String  status;
        private final String  awayTime;
        private final Integer personCount;
    }
}
