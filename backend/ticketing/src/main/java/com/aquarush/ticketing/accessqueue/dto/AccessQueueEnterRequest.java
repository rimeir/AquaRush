package com.aquarush.ticketing.accessqueue.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessQueueEnterRequest {

    @Min(value = 1, message = "봇 수는 1 이상이어야 합니다.")
    @Max(value = 10000)
    private int botCount;

    private long arrivalVirtualMs;

    /** 9시까지 남은 실제 시간(초) — pre-populate 비율 결정에 사용 */
    private int secondsUntilOpen;
}
