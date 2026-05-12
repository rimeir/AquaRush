package com.aquarush.ticketing.waitingqueue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대기열 토큰 DTO
 *
 * 대기열 진입 시 발급되는 정보
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "대기열 토큰")
public class WaitingQueueToken {

    @Schema(description = "사용자 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String userId;

    @Schema(description = "강좌 ID", example = "1")
    private Long courseId;

    @Schema(description = "대기 순번 (1부터 시작)", example = "42")
    private Long rank;

    @Schema(description = "예상 대기 시간 (초)", example = "120")
    private Integer estimatedWaitTime;

    @Schema(description = "진입 시간 (타임스탬프)", example = "1638259200000")
    private Long enteredAt;
}
