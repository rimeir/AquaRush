package com.aquarush.ticketing.accessqueue.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccessQueueEnterResponse {
    private String queueToken;
    private long position;
    private long initialPosition;
    private int estimatedWaitSeconds;
}
