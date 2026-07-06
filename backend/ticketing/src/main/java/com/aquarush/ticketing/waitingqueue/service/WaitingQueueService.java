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
 * queue:course:{id}   (ZSet, score=진입시각)  — 대기 중인 사용자
 * allowed:course:{id} (ZSet, score=만료시각)  — 예약 허용된 사용자
 *
 * 스케줄러가 1초마다 allowed ZSet의 만료 항목을 제거한 뒤
 * 빈 슬롯만큼 queue ZSet 상위 사용자를 allowed ZSet으로 이동시킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_ACTIVE_USERS = 10;
    private static final long SLOT_TTL_MS = 30_000;

    /**
     * 대기열 진입
     */
    public WaitingQueueToken enterQueue(String userId, Long courseId) {
        String queueKey = getQueueKey(courseId);
        long now = System.currentTimeMillis();

        redisTemplate.opsForZSet().add(queueKey, userId, now);

        Long rank = getQueuePosition(userId, courseId);
        int estimatedWaitTime = calculateEstimatedWaitTime(rank);

        log.info("대기열 진입: userId={}, courseId={}, rank={}", userId, courseId, rank);

        return WaitingQueueToken.builder()
                .userId(userId)
                .courseId(courseId)
                .rank(rank)
                .estimatedWaitTime(estimatedWaitTime)
                .enteredAt(now)
                .build();
    }

    /**
     * 대기 순번 조회 (queue ZSet 기준, 허용된 사용자는 null 반환)
     */
    public Long getQueuePosition(String userId, Long courseId) {
        Long rank = redisTemplate.opsForZSet().rank(getQueueKey(courseId), userId);
        return rank != null ? rank + 1 : null;
    }

    /**
     * 대기열 길이 조회
     */
    public Long getQueueLength(Long courseId) {
        Long size = redisTemplate.opsForZSet().size(getQueueKey(courseId));
        return size != null ? size : 0L;
    }

    /**
     * 예약 허용 여부 확인 — allowed ZSet 존재 여부 및 만료 시각 검사
     */
    public boolean isAllowedToReserve(String userId, Long courseId) {
        Double expireAt = redisTemplate.opsForZSet().score(getAllowedKey(courseId), userId);
        return expireAt != null && expireAt > System.currentTimeMillis();
    }

    /**
     * 이미 허용 상태이거나 대기열에 있는지 확인
     */
    public boolean isInQueueOrAllowed(String userId, Long courseId) {
        return getQueuePosition(userId, courseId) != null
                || isAllowedToReserve(userId, courseId);
    }

    /**
     * 대기열 및 허용 Set에서 제거 (예약 성공 또는 이탈 시)
     */
    public void removeFromQueue(String userId, Long courseId) {
        redisTemplate.opsForZSet().remove(getQueueKey(courseId), userId);
        redisTemplate.opsForZSet().remove(getAllowedKey(courseId), userId);
        log.info("대기열 이탈: userId={}, courseId={}", userId, courseId);
    }

    /**
     * 대기열 전체 초기화
     */
    public void clearQueue(Long courseId) {
        redisTemplate.delete(getQueueKey(courseId));
        redisTemplate.delete(getAllowedKey(courseId));
        log.info("대기열 초기화: courseId={}", courseId);
    }

    /**
     * 능동 입장 처리 (1초마다)
     *
     * 1. allowed ZSet에서 만료된 항목 제거
     * 2. 빈 슬롯 수만큼 queue ZSet 상위 사용자를 allowed ZSet으로 이동
     */
    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        Set<String> keys = redisTemplate.keys("queue:course:*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            String courseIdStr = key.replace("queue:course:", "");
            try {
                admitTopUsers(Long.parseLong(courseIdStr));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void admitTopUsers(Long courseId) {
        String queueKey = getQueueKey(courseId);
        String allowedKey = getAllowedKey(courseId);
        long now = System.currentTimeMillis();

        // 만료된 허용 슬롯 제거
        redisTemplate.opsForZSet().removeRangeByScore(allowedKey, 0, now);

        // 현재 허용 인원 확인 후 빈 슬롯 계산
        Long currentAllowed = redisTemplate.opsForZSet().size(allowedKey);
        if (currentAllowed == null) currentAllowed = 0L;

        long toAdmit = MAX_ACTIVE_USERS - currentAllowed;
        if (toAdmit <= 0) return;

        // queue 상위 N명 허용 ZSet으로 이동
        Set<Object> candidates = redisTemplate.opsForZSet().range(queueKey, 0, toAdmit - 1);
        if (candidates == null || candidates.isEmpty()) return;

        long expireAt = now + SLOT_TTL_MS;
        for (Object userId : candidates) {
            redisTemplate.opsForZSet().remove(queueKey, userId);
            redisTemplate.opsForZSet().add(allowedKey, userId, expireAt);
            log.info("예약 허용 입장: userId={}, courseId={}", userId, courseId);
        }
    }

    private int calculateEstimatedWaitTime(Long rank) {
        if (rank == null || rank <= MAX_ACTIVE_USERS) return 0;
        return (int) Math.ceil((double) (rank - MAX_ACTIVE_USERS) / MAX_ACTIVE_USERS);
    }

    private String getQueueKey(Long courseId) {
        return "queue:course:" + courseId;
    }

    private String getAllowedKey(Long courseId) {
        return "allowed:course:" + courseId;
    }
}
