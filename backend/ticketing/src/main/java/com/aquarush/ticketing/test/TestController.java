package com.aquarush.ticketing.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 유량제어 테스트용 임시 컨트롤러
 * Lombok 없이 작성된 버전
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    // @Slf4j 대신
    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    /**
     * 유량제어 테스트 엔드포인트
     */
    @GetMapping("/ratelimit")
    public ResponseEntity<Map<String, Object>> testRateLimit(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        log.info("✅ 유량제어 테스트 성공: sessionId={}", sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "요청 성공! 유량제어가 정상 동작합니다.");
        response.put("sessionId", sessionId);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * 헬스체크 엔드포인트
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.info("헬스체크 요청");

        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Server is running");

        return ResponseEntity.ok(response);
    }
}