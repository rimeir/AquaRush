package com.aquarush.ticketing.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "시뮬레이션 현황")
public class SimulationStatusResponse {

    @Schema(description = "시뮬레이션 ID")
    private String simulationId;

    @Schema(description = "강좌 ID")
    private Long courseId;

    @Schema(description = "강좌명")
    private String courseName;

    @Schema(description = "센터명")
    private String centerName;

    @Schema(description = "요일")
    private String weekdays;

    @Schema(description = "시간대")
    private String timeSlot;

    @Schema(description = "소분류 (난이도)")
    private String level;

    @Schema(description = "교육 대상")
    private String targetAudience;

    @Schema(description = "내 닉네임")
    private String myNickname;

    @Schema(description = "시뮬레이션 상태", example = "RUNNING")
    private String status;

    @Schema(description = "총 참가자 수 (나 + 봇)")
    private Integer totalParticipants;

    @Schema(description = "예약 성공 수")
    private Integer successCount;

    @Schema(description = "예약 실패 수")
    private Integer failCount;

    @Schema(description = "총 정원")
    private Integer totalSeats;

    @Schema(description = "남은 좌석")
    private Integer remainingSeats;

    @Schema(description = "대기열 길이")
    private Long queueLength;

    @Schema(description = "내 대기 순번")
    private Long myRank;

    @Schema(description = "예상 대기 시간 (초)")
    private Integer estimatedWaitTime;

    @Schema(description = "내 예약 성공 여부")
    private Boolean myReservationSuccess;

    @Schema(description = "내 순위")
    private Integer myPosition;
}