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
}