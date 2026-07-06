package com.aquarush.ticketing.simulation.controller;

import com.aquarush.ticketing.global.dto.ApiResponse;
import com.aquarush.ticketing.simulation.dto.SimulationStartRequest;
import com.aquarush.ticketing.simulation.dto.SimulationStatusResponse;
import com.aquarush.ticketing.simulation.dto.SimulationStopRequest;
import com.aquarush.ticketing.simulation.dto.UserReserveResponse;
import com.aquarush.ticketing.simulation.entity.VirtualUser;
import com.aquarush.ticketing.simulation.service.BotService;
import com.aquarush.ticketing.simulation.service.SimulationService;
import com.aquarush.ticketing.simulation.service.VirtualUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "시뮬레이션 API")
public class SimulationController {

    private final SimulationService simulationService;
    private final VirtualUserService virtualUserService;
    private final BotService botService;

    /**
     * 시뮬레이션 시작
     */
    @Operation(
            summary = "시뮬레이션 시작",
            description = "가상 유저와 봇을 생성하고 티켓팅 시뮬레이션을 시작합니다."
    )
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<SimulationStatusResponse>> startSimulation(
            @Valid @RequestBody SimulationStartRequest request
    ) {
        log.info("POST /api/v1/simulation/start - 시뮬레이션 시작");
        log.info("courseId={}, botCount={}, nickname={}",
                request.getCourseId(), request.getBotCount(), request.getNickname());

        // 1. 가상 유저 생성 (실제 사용자)
        String sessionId = "USER_" + System.currentTimeMillis();
        VirtualUser user = virtualUserService.createVirtualUser(
                sessionId, request.getNickname());

        // 2. 봇 생성
        List<VirtualUser> bots = botService.createBots(request.getBotCount());

        // 3. 시뮬레이션 세션 생성
        String simulationId = simulationService.createSimulation(
                request.getCourseId(), user, bots,
                request.getTotalSeats(), request.getRemainingSeats());

        // 4. 비동기로 봇 시뮬레이션 시작 (True Method B: 각 봇이 자신의 bot:N 슬롯 직접 감시)
        simulationService.startBotSimulation(simulationId, request.getCourseId(), bots, request.getQueueToken());

        // 5. 현황 조회
        SimulationStatusResponse status = simulationService.getStatus(simulationId);

        return ResponseEntity.ok(
                ApiResponse.success(status, "시뮬레이션이 시작되었습니다.")
        );
    }

    /**
     * 시뮬레이션 현황 조회
     */
    @Operation(
            summary = "시뮬레이션 현황 조회",
            description = "진행 중인 시뮬레이션의 현재 상태를 조회합니다."
    )
    @GetMapping("/status/{simulationId}")
    public ResponseEntity<ApiResponse<SimulationStatusResponse>> getStatus(
            @PathVariable String simulationId
    ) {
        log.info("GET /api/v1/simulation/status/{}", simulationId);

        SimulationStatusResponse status = simulationService.getStatus(simulationId);

        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * 시뮬레이션 종료
     */
    @Operation(
            summary = "시뮬레이션 종료",
            description = "진행 중인 시뮬레이션을 즉시 종료합니다. 봇 스레드는 최대 2초 내에 중단됩니다."
    )
    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<SimulationStatusResponse>> stopSimulation(
            @Valid @RequestBody SimulationStopRequest request
    ) {
        log.info("POST /api/v1/simulation/stop - simulationId={}", request.getSimulationId());

        SimulationStatusResponse status = simulationService.stopSimulation(request.getSimulationId());

        return ResponseEntity.ok(
                ApiResponse.success(status, "시뮬레이션이 종료되었습니다.")
        );
    }

    /**
     * 사용자 수동 예약 (결제 완료 시 호출)
     */
    @Operation(summary = "사용자 예약", description = "결제 완료 시 사용자가 직접 좌석을 예약합니다.")
    @PostMapping("/{simulationId}/reserve")
    public ResponseEntity<ApiResponse<UserReserveResponse>> reserveForUser(
            @PathVariable String simulationId
    ) {
        log.info("POST /api/v1/simulation/{}/reserve", simulationId);
        UserReserveResponse result = simulationService.reserveForUser(simulationId);
        String message = result.isReserved() ? "예약 완료" : "예약 실패: " + result.getFailReason();
        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    /**
     * 실시간 통계 스트림 (SSE)
     */
    @Operation(
            summary = "실시간 통계 스트림",
            description = "SSE를 통해 시뮬레이션 현황을 실시간으로 전송합니다."
    )
    @GetMapping(value = "/live/{simulationId}", produces = "text/event-stream")
    public SseEmitter streamStatus(@PathVariable String simulationId) {
        log.info("GET /api/v1/simulation/live/{} - SSE 연결", simulationId);

        return simulationService.streamStatus(simulationId);
    }
}