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
    private long totalBots;
    private long botsInQueue;
    private long botsAdmitted;

    public static AccessQueueStatusResponse granted() {
        return AccessQueueStatusResponse.builder()
                .isGranted(true)
                .position(0)
                .initialPosition(0)
                .estimatedWaitSeconds(0)
                .build();
    }
}
