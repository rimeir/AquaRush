package com.aquarush.ticketing.accessqueue.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccessQueueStatusResponse {
    @JsonProperty("isGranted")
    private boolean isGranted;
    private long position;
    private long initialPosition;
    private int estimatedWaitSeconds;

    public static AccessQueueStatusResponse granted() {
        return AccessQueueStatusResponse.builder()
                .isGranted(true)
                .position(0)
                .initialPosition(0)
                .estimatedWaitSeconds(0)
                .build();
    }
}
