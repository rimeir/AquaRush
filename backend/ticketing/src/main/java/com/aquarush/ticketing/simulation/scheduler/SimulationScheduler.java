package com.aquarush.ticketing.simulation.scheduler;

import com.aquarush.ticketing.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationScheduler {

    private final SimulationService simulationService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 실시간 대시보드 업데이트
     * 1초마다 모든 활성 시뮬레이션에 SSE 전송
     */
    @Scheduled(fixedDelay = 1000) // 1초
    public void broadcastSimulationUpdates() {
        // Redis에서 모든 시뮬레이션 키 조회
        Set<String> keys = redisTemplate.keys("simulation:*");

        if (keys == null || keys.isEmpty()) {
            return;
        }

        // 각 시뮬레이션에 업데이트 전송
        for (String key : keys) {
            String simulationId = key.replace("simulation:", "");

            try {
                // 상태 확인
                Object statusObj = redisTemplate.opsForHash().get(key, "status");
                if (statusObj == null) {
                    continue;
                }

                String status = statusObj.toString();

                // RUNNING 상태만 업데이트
                if ("RUNNING".equals(status)) {
                    simulationService.broadcastUpdate(simulationId);
                }

            } catch (Exception e) {
                log.error("시뮬레이션 업데이트 전송 실패: simulationId={}, error={}",
                        simulationId, e.getMessage());
            }
        }
    }
}