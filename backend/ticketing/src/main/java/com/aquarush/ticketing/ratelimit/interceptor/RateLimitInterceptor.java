package com.aquarush.ticketing.ratelimit.interceptor;

import com.aquarush.ticketing.ratelimit.exception.RateLimitExceededException;
import com.aquarush.ticketing.ratelimit.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 유량제어 인터셉터
 *
 * 역할:
 * 1. 모든 API 요청 전에 유량제어 확인
 * 2. 제한 초과 시 요청 차단
 * 3. 응답 헤더에 제한 정보 추가
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    // 유량제어 설정
    private static final int LIMIT = 5;           // 1분에 5회
    private static final int WINDOW_SECONDS = 60; // 60초 윈도우

    /**
     * 컨트롤러 실행 전에 호출
     *
     * @return true: 계속 진행, false: 중단
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // 사용자 식별 (세션 ID 또는 IP 주소)
        String userId = getUserIdentifier(request);

        // 유량제어 확인
        boolean allowed = rateLimiterService.isAllowed(
                userId, LIMIT, WINDOW_SECONDS);

        // 응답 헤더 추가
        addRateLimitHeaders(response, userId);

        // 제한 초과 시 예외 발생
        if (!allowed) {
            long resetTime = rateLimiterService.getTimeUntilReset(userId);

            throw new RateLimitExceededException(
                    String.format("요청 한도 초과: 1분에 %d회 제한. %d초 후 재시도 가능",
                            LIMIT, resetTime)
            );
        }

        return true;
    }

    /**
     * 사용자 식별자 추출
     *
     * 우선순위:
     * 1. 세션 ID (있으면)
     * 2. IP 주소 (없으면)
     */
    private String getUserIdentifier(HttpServletRequest request) {
        // 1. 세션 ID 확인
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }

        // 2. IP 주소 사용
        String clientIp = getClientIp(request);
        return "ip:" + clientIp;
    }

    /**
     * 클라이언트 IP 주소 추출
     *
     * 프록시 환경 고려:
     * - X-Forwarded-For: 프록시 통과한 원본 IP
     * - X-Real-IP: Nginx 등이 설정
     * - RemoteAddr: 직접 연결 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 응답 헤더에 유량제어 정보 추가
     *
     * 표준 헤더:
     * - X-RateLimit-Limit: 전체 제한
     * - X-RateLimit-Remaining: 남은 횟수
     * - X-RateLimit-Reset: 리셋 시간
     */
    private void addRateLimitHeaders(HttpServletResponse response, String userId) {
        int remaining = rateLimiterService.getRemainingRequests(userId, LIMIT);
        long resetTime = rateLimiterService.getTimeUntilReset(userId);

        response.setHeader("X-RateLimit-Limit", String.valueOf(LIMIT));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
    }
}
