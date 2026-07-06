package com.aquarush.ticketing.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "시뮬레이션 시작 요청")
public class SimulationStartRequest {

    @NotNull(message = "강좌 ID는 필수입니다.")
    @Schema(description = "강좌 ID", example = "1")
    private Long courseId;

    @Min(value = 10, message = "최소 10명 이상의 봇이 필요합니다.")
    @Max(value = 10000, message = "최대 10000명까지 설정 가능합니다.")
    @Schema(description = "봇 수", example = "1000")
    private Integer botCount;

    @Size(max = 50, message = "닉네임은 50자 이내로 입력해주세요.")
    @Schema(description = "닉네임 (선택)", example = "티켓팅마스터")
    private String nickname;

    @Min(value = 1, message = "총 정원은 1 이상이어야 합니다.")
    @Max(value = 1000, message = "총 정원은 1000 이하로 설정해주세요.")
    @Schema(description = "시뮬레이션 총 정원 (기본값: 강좌 기본 정원)", example = "20")
    private Integer totalSeats;

    @Min(value = 1, message = "남은 좌석은 1 이상이어야 합니다.")
    @Schema(description = "남은 예약 좌석 수 (기본값: 강좌 기본 정원)", example = "5")
    private Integer remainingSeats;

    @Schema(description = "유량제어 대기열 토큰 (선택 — 없으면 대기열 없이 즉시 시작)", example = "uuid-...")
    private String queueToken;
}