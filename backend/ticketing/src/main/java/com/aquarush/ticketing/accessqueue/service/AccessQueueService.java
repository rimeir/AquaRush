package com.aquarush.ticketing.accessqueue.service;

import com.aquarush.ticketing.accessqueue.dto.AccessQueueEnterResponse;
import com.aquarush.ticketing.accessqueue.dto.AccessQueueStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "access:queue:";
    private static final String META_KEY_PREFIX  = "access:meta:";
    // Sorted Set 내 실제 유저를 나타내는 고정 멤버명
    private static final String USER_MEMBER = "user";

    /**
     * 접속 대기열 초기화 + 유저 진입
     *
     * Redis Sorted Set에 가상 접속자(v:N)를 사전에 채우고,
     * 실제 유저("user")를 arrivalVirtualMs 스코어로 삽입한다.
     *
     * 가상 접속자 분포:
     *   - 80% : openVirtualMs 기준 T-120s ~ T-0s 구간에 균등 분포 (미리 접속한 인원)
     *   - 20% : T-0s ~ T+10s 구간에 집중 (정각 surge)
     *
     * 입장 처리율(admissionRate): 전체 가상 접속자를 약 12초에 소화
     * 실제 오픈 시각(realOpenTime): now + (openVirtualMs - arrivalVirtualMs)
     */
    public AccessQueueEnterResponse enterQueue(int botCount, long arrivalVirtualMs) {
        String queueKey = QUEUE_KEY_PREFIX + java.util.UUID.randomUUID();
        // queueToken = queueKey 에서 prefix 제거
        String queueToken = queueKey.replace(QUEUE_KEY_PREFIX, "");
        String metaKey   = META_KEY_PREFIX + queueToken;

        int totalVirtual = botCount;
        int earlyCount   = (int) (totalVirtual * 0.8);
        Random rnd = new Random();

        // 가상 유저 전체를 Set<TypedTuple>로 모아 한 번의 ZADD로 전송 (N+1 문제 방지)
        Set<TypedTuple<Object>> tuples = new HashSet<>(totalVirtual);
        for (int i = 0; i < earlyCount; i++) {
            long offset = -(long) (rnd.nextDouble() * 120_000);
            tuples.add(new org.springframework.data.redis.core.DefaultTypedTuple<>("v:" + i, (double) (arrivalVirtualMs + offset)));
        }
        for (int i = earlyCount; i < totalVirtual; i++) {
            long offset = (long) (rnd.nextDouble() * 30_000);
            tuples.add(new org.springframework.data.redis.core.DefaultTypedTuple<>("v:" + i, (double) (arrivalVirtualMs + offset)));
        }
        redisTemplate.opsForZSet().add(queueKey, tuples);

        // 실제 유저 삽입 (이미 있으면 갱신 안 함)
        Long existingRank = redisTemplate.opsForZSet().rank(queueKey, USER_MEMBER);
        if (existingRank == null) {
            redisTemplate.opsForZSet().add(queueKey, USER_MEMBER, (double) arrivalVirtualMs);
        }

        Long rank = redisTemplate.opsForZSet().rank(queueKey, USER_MEMBER);
        long initialPosition = rank != null ? rank + 1 : 1;

        // 초당 처리율: 전체를 약 12초에 소화 (최소 5명/s)
        int admissionRate = Math.max(5, totalVirtual / 12);

        redisTemplate.opsForHash().put(metaKey, "admissionRate",   String.valueOf(admissionRate));
        redisTemplate.opsForHash().put(metaKey, "initialPosition", String.valueOf(initialPosition));

        int estimatedWait = (int) Math.ceil((double) initialPosition / admissionRate);

        log.info("접속 대기열 진입: token={}, virtualUsers={}, position={}", queueToken, totalVirtual, initialPosition);

        return AccessQueueEnterResponse.builder()
                .queueToken(queueToken)
                .position(initialPosition)
                .initialPosition(initialPosition)
                .estimatedWaitSeconds(estimatedWait)
                .build();
    }

    /**
     * 대기열 상태 조회
     *
     * "user" 멤버가 Sorted Set에 없으면 이미 입장 처리된 것으로 간주한다.
     */
    public AccessQueueStatusResponse getStatus(String queueToken) {
        String queueKey = QUEUE_KEY_PREFIX + queueToken;
        String metaKey  = META_KEY_PREFIX + queueToken;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(queueKey))) {
            return AccessQueueStatusResponse.granted();
        }

        Long rank = redisTemplate.opsForZSet().rank(queueKey, USER_MEMBER);
        if (rank == null) {
            // 큐에서 제거됨 = 입장 허가
            return AccessQueueStatusResponse.granted();
        }

        long position = rank + 1;

        Object initPosObj = redisTemplate.opsForHash().get(metaKey, "initialPosition");
        long initialPosition = initPosObj != null ? Long.parseLong(initPosObj.toString()) : position;

        Object rateObj = redisTemplate.opsForHash().get(metaKey, "admissionRate");
        int rate = rateObj != null ? Integer.parseInt(rateObj.toString()) : 5;
        int estimatedWait = (int) Math.ceil((double) position / rate);

        return AccessQueueStatusResponse.builder()
                .isGranted(false)
                .position(position)
                .initialPosition(initialPosition)
                .estimatedWaitSeconds(estimatedWait)
                .build();
    }

    /**
     * 입장 처리 (스케줄러에서 1초마다 호출)
     *
     * realOpenTime 이전에는 실행하지 않는다.
     * Sorted Set 앞에서부터 admissionRate개 제거 → 유저("user")가 포함되면 자동으로 입장 처리.
     */
    public void processAdmissions(String queueToken) {
        String metaKey = META_KEY_PREFIX + queueToken;

        Object rateObj = redisTemplate.opsForHash().get(metaKey, "admissionRate");
        int rate = rateObj != null ? Integer.parseInt(rateObj.toString()) : 5;

        String queueKey = QUEUE_KEY_PREFIX + queueToken;
        Long size = redisTemplate.opsForZSet().size(queueKey);
        if (size == null || size == 0) {
            // 큐 비었으면 메타도 정리
            redisTemplate.delete(metaKey);
            return;
        }

        redisTemplate.opsForZSet().removeRange(queueKey, 0, rate - 1);
    }

    /**
     * 시뮬레이션 종료 또는 테스트용 정리
     */
    public void clearQueue(String queueToken) {
        redisTemplate.delete(QUEUE_KEY_PREFIX + queueToken);
        redisTemplate.delete(META_KEY_PREFIX + queueToken);
        log.info("접속 대기열 정리: token={}", queueToken);
    }

    /**
     * 모든 활성 대기열 토큰 조회 (스케줄러용)
     */
    public Set<String> getActiveQueueTokens() {
        Set<String> keys = redisTemplate.keys(QUEUE_KEY_PREFIX + "*");
        if (keys == null) return Set.of();
        Set<String> tokens = new java.util.HashSet<>();
        for (String key : keys) {
            tokens.add(key.replace(QUEUE_KEY_PREFIX, ""));
        }
        return tokens;
    }
}
