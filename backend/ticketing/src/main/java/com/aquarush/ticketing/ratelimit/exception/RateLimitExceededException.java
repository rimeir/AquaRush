package com.aquarush.ticketing.ratelimit.exception;

/**
 * 유량제한 초과 예외
 *
 * HTTP 상태 코드: 429 Too Many Requests
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
