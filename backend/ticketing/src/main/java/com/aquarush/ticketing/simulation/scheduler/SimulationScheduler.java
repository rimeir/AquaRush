package com.aquarush.ticketing.simulation.scheduler;

import com.aquarush.ticketing.accessqueue.service.AccessQueueService;
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
    private final AccessQueueService accessQueueService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 접속 대기열 입장 처리 (1초마다)
     *
     * True Method B: 스케줄러는 대기열 멤버를 제거하기만 함.
     * 각 봇 스레드는 자신의 bot:N 슬롯을 폴링하여 입장 여부를 독립적으로 감지한다.
     * admitBots() 호출 불필요.
     */
    @Scheduled(fixedDelay = 1000)
    public void processAccessQueues() {
        Set<String> tokens = accessQueueService.getActiveQueueTokens();
        for (String token : tokens) {
            try {
                accessQueueService.processAdmissions(token);
            } catch (Exception e) {
                log.error("접속 대기열 처리 실패: token={}, error={}", token, e.getMessage());
            }
        }
    }

    /**
     * 실시간 대시보드 업데이트 (1초마다)
     */
    @Scheduled(fixedDelay = 1000)
    public void broadcastSimulationUpdates() {
        Set<String> keys = redisTemplate.keys("simulation:*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            String simulationId = key.replace("simulation:", "");
            try {
                Object statusObj = redisTemplate.opsForHash().get(key, "status");
                if (statusObj == null) continue;
                if ("RUNNING".equals(statusObj.toString())) {
                    simulationService.broadcastUpdate(simulationId);
                }
            } catch (Exception e) {
                log.error("시뮬레이션 업데이트 전송 실패: simulationId={}, error={}", simulationId, e.getMessage());
            }
        }
    }
}
