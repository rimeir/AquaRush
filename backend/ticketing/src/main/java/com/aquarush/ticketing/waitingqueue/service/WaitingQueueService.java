package com.aquarush.ticketing.waitingqueue.service;

import com.aquarush.ticketing.waitingqueue.dto.WaitingQueueToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 대기열 관리 서비스
 *
 * 주요 기능:
 * 1. 대기열 진입
 * 2. 순위 조회
 * 3. 예약 허용 여부 확인
 * 4. 대기열 처리 (스케줄러)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    // 동시 처리 가능한 사용자 수
    private static final int MAX_ACTIVE_USERS = 10;

    /**
     * 대기열 진입
     *
     * @param userId 사용자 ID (세션 ID)
     * @param courseId 강좌 ID
     * @return 대기열 토큰
     *
     * 동작:
     * 1. Redis Sorted Set에 추가
     * 2. Score = 현재 시간 (밀리초)
     * 3. 자동으로 Score 순 정렬
     */
    public WaitingQueueToken enterQueue(String userId, Long courseId) {
        String queueKey = getQueueKey(courseId);

        // 현재 시간을 Score로 사용
        long now = System.currentTimeMillis();

        // Sorted Set에 추가
        redisTemplate.opsForZSet().add(queueKey, userId, now);

        // 순위 조회 (0부터 시작하므로 +1)
        Long rank = getQueuePosition(userId, courseId);

        // 예상 대기 시간 계산 (초당 10명 처리 가정)
        int estimatedWaitTime = calculateEstimatedWaitTime(rank);

        log.info("대기열 진입: userId={}, courseId={}, rank={}",
                userId, courseId, rank);

        return WaitingQueueToken.builder()
                .userId(userId)
                .courseId(courseId)
                .rank(rank)
                .estimatedWaitTime(estimatedWaitTime)
                .enteredAt(now)
                .build();
    }

    /**
     * 대기 순번 조회
     *
     * @param userId 사용자 ID
     * @param courseId 강좌 ID
     * @return 순번 (1부터 시작), 없으면 null
     *
     * Redis 명령:
     * ZRANK queue:course:1 user123
     * → 0부터 시작하는 인덱스 반환
     */
    public Long getQueuePosition(String userId, Long courseId) {
        String queueKey = getQueueKey(courseId);

        // ZRANK: 0부터 시작하는 인덱스 반환
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);

        // null이면 대기열에 없음
        if (rank == null) {
            return null;
        }

        // 1부터 시작하도록 +1
        return rank + 1;
    }

    /**
     * 대기열 길이 조회
     *
     * @param courseId 강좌 ID
     * @return 대기 중인 사용자 수
     */
    public Long getQueueLength(Long courseId) {
        String queueKey = getQueueKey(courseId);

        // ZCARD: Sorted Set의 크기
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0L;
    }

    /**
     * 예약 허용 여부 확인
     *
     * @param userId 사용자 ID
     * @param courseId 강좌 ID
     * @return true: 예약 가능, false: 대기 필요
     *
     * 조건:
     * - 순위가 MAX_ACTIVE_USERS 이내
     */
    public boolean isAllowedToReserve(String userId, Long courseId) {
        Long rank = getQueuePosition(userId, courseId);

        if (rank == null) {
            // 대기열에 없음 → 먼저 진입해야 함
            return false;
        }

        // 상위 10명 이내면 예약 가능
        return rank <= MAX_ACTIVE_USERS;
    }

    /**
     * 대기열에서 제거
     *
     * @param userId 사용자 ID
     * @param courseId 강좌 ID
     *
     * 호출 시점:
     * - 예약 성공 후
     * - 사용자가 포기한 경우
     */
    public void removeFromQueue(String userId, Long courseId) {
        String queueKey = getQueueKey(courseId);

        // ZREM: Sorted Set에서 제거
        redisTemplate.opsForZSet().remove(queueKey, userId);

        log.info("대기열 이탈: userId={}, courseId={}", userId, courseId);
    }

    /**
     * 대기열 초기화
     *
     * @param courseId 강좌 ID
     */
    public void clearQueue(Long courseId) {
        String queueKey = getQueueKey(courseId);
        redisTemplate.delete(queueKey);
        log.info("대기열 초기화: courseId={}", courseId);
    }

    /**
     * 대기열 처리 (스케줄러) — 1초마다 활성 대기열 현황 로깅
     */
    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        Set<String> keys = redisTemplate.keys("queue:course:*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            String courseIdStr = key.replace("queue:course:", "");
            try {
                processQueueForCourse(Long.parseLong(courseIdStr));
            } catch (NumberFormatException ignored) {}
        }
    }

    /**
     * 특정 강좌의 대기열 처리
     */
    private void processQueueForCourse(Long courseId) {
        String queueKey = getQueueKey(courseId);

        // 상위 10명 조회
        var topUsers = redisTemplate.opsForZSet()
                .range(queueKey, 0, MAX_ACTIVE_USERS - 1);

        if (topUsers != null && !topUsers.isEmpty()) {
            log.debug("대기열 처리: courseId={}, 활성 사용자={}",
                    courseId, topUsers.size());
        }
    }

    /**
     * 예상 대기 시간 계산
     *
     * @param rank 순위
     * @return 예상 대기 시간 (초)
     *
     * 계산 방식:
     * - 10명씩 처리
     * - 1초에 10명 처리 가정
     * - 순위 / 10 = 대기 시간 (초)
     */
    private int calculateEstimatedWaitTime(Long rank) {
        if (rank == null || rank <= MAX_ACTIVE_USERS) {
            return 0;  // 즉시 처리 가능
        }

        // (순위 - 10) / 10 = 대기 초
        // 예: 100등 → (100 - 10) / 10 = 9초
        return (int) Math.ceil((double) (rank - MAX_ACTIVE_USERS) / MAX_ACTIVE_USERS);
    }

    /**
     * Redis 키 생성
     *
     * @param courseId 강좌 ID
     * @return "queue:course:{courseId}"
     */
    private String getQueueKey(Long courseId) {
        return "queue:course:" + courseId;
    }
}
