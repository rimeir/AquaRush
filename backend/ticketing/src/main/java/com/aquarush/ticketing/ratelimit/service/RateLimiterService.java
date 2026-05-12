package com.aquarush.ticketing.ratelimit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 유량제어 서비스 (Token Bucket 알고리즘)
 *
 * 목적:
 * 1. 과도한 요청 차단
 * 2. 서버 보호
 * 3. 공정한 자원 분배
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 요청 허용 여부 확인
     *
     * @param userId 사용자 ID (세션 ID)
     * @param limit 제한 횟수 (예: 5)
     * @param windowSeconds 시간 윈도우 (초 단위, 예: 60)
     * @return true: 허용, false: 거부
     *
     * 예시:
     * isAllowed("user123", 5, 60)
     * → 1분에 5회까지 허용
     */
    public boolean isAllowed(String userId, int limit, int windowSeconds) {
        // Redis 키 생성: "ratelimit:user123"
        String key = "ratelimit:" + userId;

        try {
            // 현재 카운트 증가 (원자적 연산)
            Long currentCount = redisTemplate.opsForValue().increment(key);

            // 첫 요청일 때만 TTL 설정
            if (currentCount == 1) {
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            }

            // 제한 확인
            boolean allowed = currentCount <= limit;

            if (allowed) {
                log.debug("요청 허용: userId={}, count={}/{}",
                        userId, currentCount, limit);
            } else {
                log.warn("요청 거부: userId={}, count={}/{} (제한 초과)",
                        userId, currentCount, limit);
            }

            return allowed;

        } catch (Exception e) {
            log.error("유량제어 확인 중 오류 발생: userId={}", userId, e);
            // 오류 시 허용 (Fail-open 정책)
            return true;
        }
    }

    /**
     * 남은 요청 횟수 조회
     *
     * @param userId 사용자 ID
     * @param limit 제한 횟수
     * @return 남은 횟수 (0 이상)
     */
    public int getRemainingRequests(String userId, int limit) {
        String key = "ratelimit:" + userId;

        try {
            Object value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return limit;  // 아직 요청 안 함
            }

            int currentCount = Integer.parseInt(value.toString());
            int remaining = limit - currentCount;

            return Math.max(0, remaining);  // 음수 방지

        } catch (Exception e) {
            log.error("남은 요청 횟수 조회 중 오류", e);
            return 0;
        }
    }

    /**
     * 다음 윈도우까지 남은 시간 (초)
     *
     * @param userId 사용자 ID
     * @return 남은 시간 (초), 없으면 0
     */
    public long getTimeUntilReset(String userId) {
        String key = "ratelimit:" + userId;

        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null && ttl > 0 ? ttl : 0;

        } catch (Exception e) {
            log.error("리셋 시간 조회 중 오류", e);
            return 0;
        }
    }

    /**
     * 유량제한 초기화 (관리자용)
     *
     * @param userId 사용자 ID
     */
    public void resetRateLimit(String userId) {
        String key = "ratelimit:" + userId;
        redisTemplate.delete(key);
        log.info("유량제한 초기화: userId={}", userId);
    }
}
