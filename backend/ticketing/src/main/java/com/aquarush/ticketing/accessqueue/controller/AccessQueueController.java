package com.aquarush.ticketing.accessqueue.controller;

import com.aquarush.ticketing.accessqueue.dto.AccessQueueEnterRequest;
import com.aquarush.ticketing.accessqueue.dto.AccessQueueEnterResponse;
import com.aquarush.ticketing.accessqueue.dto.AccessQueueStatusResponse;
import com.aquarush.ticketing.accessqueue.service.AccessQueueService;
import com.aquarush.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/access-queue")
@RequiredArgsConstructor
@Tag(name = "AccessQueue", description = "접속 대기열 API")
public class AccessQueueController {

    private final AccessQueueService accessQueueService;

    @Operation(summary = "대기열 진입",
               description = "가상 접속자를 포함한 Sorted Set을 생성하고 유저를 진입시킵니다. 초기 순번과 queueToken을 반환합니다.")
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<AccessQueueEnterResponse>> enter(
            @Valid @RequestBody AccessQueueEnterRequest request
    ) {
        log.info("POST /api/v1/access-queue/enter - botCount={}", request.getBotCount());
        AccessQueueEnterResponse response = accessQueueService.enterQueue(
                request.getBotCount(),
                request.getArrivalVirtualMs(),
                request.getSecondsUntilOpen()
        );
        return ResponseEntity.ok(ApiResponse.success(response, "대기열에 진입했습니다."));
    }

    @Operation(summary = "대기열 상태 조회",
               description = "현재 순번과 입장 허가 여부를 반환합니다. isGranted=true이면 접속 가능합니다.")
    @GetMapping("/status/{queueToken}")
    public ResponseEntity<ApiResponse<AccessQueueStatusResponse>> status(
            @PathVariable String queueToken
    ) {
        AccessQueueStatusResponse response = accessQueueService.getStatus(queueToken);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
