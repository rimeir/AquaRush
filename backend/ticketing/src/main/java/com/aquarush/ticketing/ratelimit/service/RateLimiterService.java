package com.aquarush.ticketing.ratelimit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;

    // 슬라이딩 윈도우: 윈도우 밖 항목 제거 → 카운트 확인 → 한도 미만이면 추가 (원자적)
    private static final String SLIDING_WINDOW_SCRIPT =
        "local key = KEYS[1]\n" +
        "local now = tonumber(ARGV[1])\n" +
        "local window = tonumber(ARGV[2])\n" +
        "local limit = tonumber(ARGV[3])\n" +
        "local member = ARGV[4]\n" +
        "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
        "local count = redis.call('ZCARD', key)\n" +
        "if count < limit then\n" +
        "  redis.call('ZADD', key, now, member)\n" +
        "  redis.call('EXPIRE', key, math.ceil(window / 1000) + 1)\n" +
        "  return 1\n" +
        "else\n" +
        "  return 0\n" +
        "end";

    public boolean isAllowed(String userId, int limit, int windowSeconds) {
        String key = "ratelimit:" + userId;
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000;
        String member = now + "-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
            Long result = stringRedisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(windowMs),
                String.valueOf(limit),
                member
            );
            boolean allowed = Long.valueOf(1L).equals(result);
            if (!allowed) {
                log.warn("요청 거부 (슬라이딩 윈도우): userId={}", userId);
            }
            return allowed;
        } catch (Exception e) {
            log.error("유량제어 확인 중 오류: userId={}", userId, e);
            return true;
        }
    }

    public int getRemainingRequests(String userId, int limit, int windowSeconds) {
        String key = "ratelimit:" + userId;
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000;

        try {
            stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, now - windowMs);
            Long count = stringRedisTemplate.opsForZSet().zCard(key);
            return Math.max(0, (int) (limit - (count != null ? count : 0)));
        } catch (Exception e) {
            log.error("남은 요청 횟수 조회 중 오류", e);
            return 0;
        }
    }

    // 윈도우 안 가장 오래된 요청이 윈도우 밖으로 나갈 때까지 남은 초
    public long getTimeUntilReset(String userId, int windowSeconds) {
        String key = "ratelimit:" + userId;
        long now = System.currentTimeMillis();
        long windowMs = (long) windowSeconds * 1000;

        try {
            Set<ZSetOperations.TypedTuple<String>> oldest =
                stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
            if (oldest == null || oldest.isEmpty()) return 0;

            Double oldestScore = oldest.iterator().next().getScore();
            if (oldestScore == null) return 0;

            long remaining = (oldestScore.longValue() + windowMs - now) / 1000;
            return Math.max(0, remaining);
        } catch (Exception e) {
            log.error("리셋 시간 조회 중 오류", e);
            return 0;
        }
    }
}
