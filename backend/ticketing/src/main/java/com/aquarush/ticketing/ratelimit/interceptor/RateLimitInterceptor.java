package com.aquarush.ticketing.ratelimit.interceptor;

import com.aquarush.ticketing.ratelimit.exception.RateLimitExceededException;
import com.aquarush.ticketing.ratelimit.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterService rateLimiterService;

    private static final int LIMIT = 5;
    private static final int WINDOW_SECONDS = 60;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String userId = getUserIdentifier(request);
        boolean allowed = rateLimiterService.isAllowed(userId, LIMIT, WINDOW_SECONDS);

        addRateLimitHeaders(response, userId);

        if (!allowed) {
            long resetTime = rateLimiterService.getTimeUntilReset(userId, WINDOW_SECONDS);
            throw new RateLimitExceededException(
                String.format("요청 한도 초과: 1분에 %d회 제한. %d초 후 재시도 가능", LIMIT, resetTime)
            );
        }

        return true;
    }

    private String getUserIdentifier(HttpServletRequest request) {
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank()) ip = request.getRemoteAddr();
        return ip;
    }

    private void addRateLimitHeaders(HttpServletResponse response, String userId) {
        int remaining = rateLimiterService.getRemainingRequests(userId, LIMIT, WINDOW_SECONDS);
        long resetTime = rateLimiterService.getTimeUntilReset(userId, WINDOW_SECONDS);

        response.setHeader("X-RateLimit-Limit", String.valueOf(LIMIT));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
    }
}
