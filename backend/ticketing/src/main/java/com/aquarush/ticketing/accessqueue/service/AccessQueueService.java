package com.aquarush.ticketing.accessqueue.service;

import com.aquarush.ticketing.accessqueue.dto.AccessQueueEnterResponse;
import com.aquarush.ticketing.accessqueue.dto.AccessQueueStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessQueueService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "access:queue:";
    private static final String META_KEY_PREFIX  = "access:meta:";
    private static final String USER_MEMBER      = "user";

    /**
     * 유저 대기열 진입
     *
     * secondsUntilOpen 기반으로 사전 봇 수 결정:
     * - 9시까지 30초 이상: 봇 10% 미리 진입 (소수만 먼저 접속한 상태)
     * - 9시까지 10초: 봇 70% 미리 진입 (막바지에 몰린 상태)
     * - 9시 이후: 봇 80% 미리 진입
     *
     * 나머지 봇은 BotService가 9시에 staggered로 추가 (내 뒤에 N명 동적 증가)
     */
    public AccessQueueEnterResponse enterQueue(int botCount, long arrivalVirtualMs, int secondsUntilOpen) {
        String queueToken = UUID.randomUUID().toString();
        String queueKey   = QUEUE_KEY_PREFIX + queueToken;
        String metaKey    = META_KEY_PREFIX + queueToken;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // secondsUntilOpen: 가상 시계 기준 0~60 가상초
        // 60초 남음(페이지 로드) → 봇 10%, 0초(9시 직전) → 봇 80%
        double prePopRatio = Math.max(0.10, Math.min(0.80,
                (60.0 - Math.max(0, secondsUntilOpen)) / 60.0 * 0.80));
        int earlyBotCount = Math.max(5, (int)(botCount * prePopRatio));

        // 조기 봇 진입: 80%는 유저보다 앞, 20%는 유저보다 뒤
        int aheadCount  = (int)(earlyBotCount * 0.80);
        int behindCount = earlyBotCount - aheadCount;

        for (int i = 0; i < aheadCount; i++) {
            long offset = (long)(rnd.nextDouble() * 120_000);
            redisTemplate.opsForZSet().add(queueKey, "bot:" + i,
                    (double)(arrivalVirtualMs - offset));
        }

        // 유저 진입
        redisTemplate.opsForZSet().add(queueKey, USER_MEMBER, (double)arrivalVirtualMs);

        for (int i = aheadCount; i < earlyBotCount; i++) {
            long offset = (long)(rnd.nextDouble() * 30_000);
            redisTemplate.opsForZSet().add(queueKey, "bot:" + i,
                    (double)(arrivalVirtualMs + offset));
        }

        // 유저 순번 (조기 봇 수 + 1)
        Long rank = redisTemplate.opsForZSet().rank(queueKey, USER_MEMBER);
        long position = rank != null ? rank + 1 : earlyBotCount + 1;

        int admissionRate = Math.max(5, botCount / 12);
        int estimatedWait = (int)Math.ceil((double)(botCount + 1) / admissionRate);

        redisTemplate.opsForHash().put(metaKey, "admissionRate",    String.valueOf(admissionRate));
        redisTemplate.opsForHash().put(metaKey, "totalBots",        String.valueOf(botCount));
        redisTemplate.opsForHash().put(metaKey, "earlyBotCount",    String.valueOf(earlyBotCount));
        redisTemplate.opsForHash().put(metaKey, "botsAdmitted",     "0");
        redisTemplate.opsForHash().put(metaKey, "processingEnabled","false");
        redisTemplate.opsForHash().put(metaKey, "userVirtualMs",    String.valueOf(arrivalVirtualMs));
        redisTemplate.opsForHash().put(metaKey, "initialPosition",  String.valueOf(position));

        log.info("접속 대기열 생성: token={}, secondsUntilOpen={}s, earlyBotCount={}/{}, 유저순번={}",
                queueToken, secondsUntilOpen, earlyBotCount, botCount, position);

        return AccessQueueEnterResponse.builder()
                .queueToken(queueToken)
                .position(position)
                .initialPosition(position)
                .estimatedWaitSeconds(estimatedWait)
                .build();
    }

    /** 대기열 처리 활성화 — 9시(시뮬레이션 시작)에 BotService가 호출 */
    public void enableProcessing(String queueToken) {
        redisTemplate.opsForHash().put(META_KEY_PREFIX + queueToken, "processingEnabled", "true");
        log.info("대기열 처리 활성화: token={}", queueToken);
    }

    /** 유저 도착 가상 시각 조회 — 봇 offset 기준점 */
    public long getUserVirtualMs(String queueToken) {
        Object obj = redisTemplate.opsForHash().get(META_KEY_PREFIX + queueToken, "userVirtualMs");
        return obj != null ? Long.parseLong(obj.toString()) : System.currentTimeMillis();
    }

    /** 미리 진입한 봇 수 조회 — BotService에서 나머지 봇 처리 시 사용 */
    public int getEarlyBotCount(String queueToken) {
        Object obj = redisTemplate.opsForHash().get(META_KEY_PREFIX + queueToken, "earlyBotCount");
        return obj != null ? Integer.parseInt(obj.toString()) : 0;
    }

    /**
     * 봇 스레드가 대기열에 진입 (9시 이후 나머지 봇들)
     *
     * 음수 offset 봇: 유저보다 앞 → 유저 순번을 밀어올림
     * 양수 offset 봇: 유저보다 뒤 → "내 뒤에 N명" 증가
     */
    public void enterBotQueue(String queueToken, int botIdx, long arrivalMs) {
        String queueKey = QUEUE_KEY_PREFIX + queueToken;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(queueKey))) return;
        redisTemplate.opsForZSet().add(queueKey, "bot:" + botIdx, (double)arrivalMs);

        Long rank = redisTemplate.opsForZSet().rank(queueKey, USER_MEMBER);
        if (rank != null) {
            redisTemplate.opsForHash().put(META_KEY_PREFIX + queueToken,
                    "initialPosition", String.valueOf(rank + 1));
        }
    }

    /** 봇 슬롯이 대기열에 남아있는지 확인 (봇 스레드 폴링용) */
    public boolean isBotInQueue(String queueToken, int botIdx) {
        String queueKey = QUEUE_KEY_PREFIX + queueToken;
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(queueKey))) return false;
        return redisTemplate.opsForZSet().rank(queueKey, "bot:" + botIdx) != null;
    }

    /** 대기열 상태 조회 */
    public AccessQueueStatusResponse getStatus(String queueToken) {
        String queueKey = QUEUE_KEY_PREFIX + queueToken;
        String metaKey  = META_KEY_PREFIX + queueToken;

        if (!Boolean.TRUE.equals(redisTemplate.hasKey(queueKey))) {
            return AccessQueueStatusResponse.granted();
        }

        Long rank = redisTemplate.opsForZSet().rank(queueKey, USER_MEMBER);
        if (rank == null) return AccessQueueStatusResponse.granted();

        long position = rank + 1;

        Object initPosObj = redisTemplate.opsForHash().get(metaKey, "initialPosition");
        long initialPosition = initPosObj != null ? Long.parseLong(initPosObj.toString()) : position;

        Object rateObj = redisTemplate.opsForHash().get(metaKey, "admissionRate");
        int rate = rateObj != null ? Integer.parseInt(rateObj.toString()) : 5;
        int estimatedWait = (int)Math.ceil((double)position / rate);

        Object totalBotsObj = redisTemplate.opsForHash().get(metaKey, "totalBots");
        long totalBots = totalBotsObj != null ? Long.parseLong(totalBotsObj.toString()) : 0;

        Long queueSize = redisTemplate.opsForZSet().size(queueKey);
        long botsInQueue = Math.max(0, (queueSize != null ? queueSize : 0) - 1);

        Object admittedObj = redisTemplate.opsForHash().get(metaKey, "botsAdmitted");
        long botsAdmitted = admittedObj != null ? Long.parseLong(admittedObj.toString()) : 0;

        return AccessQueueStatusResponse.builder()
                .isGranted(false)
                .position(position)
                .initialPosition(initialPosition)
                .estimatedWaitSeconds(estimatedWait)
                .totalBots(totalBots)
                .botsInQueue(botsInQueue)
                .botsAdmitted(botsAdmitted)
                .build();
    }

    /**
     * 입장 처리 (스케줄러 1초마다)
     * processingEnabled=false 이면 skip (9시 전에는 처리 안 함)
     */
    public void processAdmissions(String queueToken) {
        String metaKey  = META_KEY_PREFIX + queueToken;
        String queueKey = QUEUE_KEY_PREFIX + queueToken;

        Object enabledObj = redisTemplate.opsForHash().get(metaKey, "processingEnabled");
        if (!"true".equals(enabledObj != null ? enabledObj.toString() : "false")) return;

        Object rateObj = redisTemplate.opsForHash().get(metaKey, "admissionRate");
        int rate = rateObj != null ? Integer.parseInt(rateObj.toString()) : 5;

        Long size = redisTemplate.opsForZSet().size(queueKey);
        if (size == null || size == 0) {
            redisTemplate.delete(metaKey);
            return;
        }

        Set<Object> toRemove = redisTemplate.opsForZSet().range(queueKey, 0, rate - 1);
        if (toRemove == null || toRemove.isEmpty()) return;

        long botCount = toRemove.stream()
                .filter(m -> !USER_MEMBER.equals(m.toString()))
                .count();

        redisTemplate.opsForZSet().removeRange(queueKey, 0, rate - 1);

        if (botCount > 0) {
            Object admittedObj = redisTemplate.opsForHash().get(metaKey, "botsAdmitted");
            long current = admittedObj != null ? Long.parseLong(admittedObj.toString()) : 0;
            redisTemplate.opsForHash().put(metaKey, "botsAdmitted", String.valueOf(current + botCount));
        }
    }

    public void clearQueue(String queueToken) {
        redisTemplate.delete(QUEUE_KEY_PREFIX + queueToken);
        redisTemplate.delete(META_KEY_PREFIX + queueToken);
    }

    public Set<String> getActiveQueueTokens() {
        Set<String> keys = redisTemplate.keys(QUEUE_KEY_PREFIX + "*");
        if (keys == null) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String key : keys) tokens.add(key.replace(QUEUE_KEY_PREFIX, ""));
        return tokens;
    }
}
