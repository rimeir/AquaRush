package com.aquarush.ticketing.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "시뮬레이션 종료 요청")
public class SimulationStopRequest {

    @NotBlank(message = "시뮬레이션 ID는 필수입니다.")
    @Schema(description = "종료할 시뮬레이션 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String simulationId;
}
