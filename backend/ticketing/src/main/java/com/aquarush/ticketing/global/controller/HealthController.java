package com.aquarush.ticketing.global.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health Check", description = "서버 상태 확인 API")
public class HealthController {

    @Operation(
            summary = "서버 상태 체크",
            description = "서버가 정상적으로 동작하는지 확인합니다."
    )
    @ApiResponse(responseCode = "200", description = "서버 정상 작동")
    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "Server is running");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Ping 테스트")
    @ApiResponse(responseCode = "200", description = "Pong 응답")
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "pong");
        return ResponseEntity.ok(response);
    }
}