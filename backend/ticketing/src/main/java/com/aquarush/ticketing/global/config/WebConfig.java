package com.aquarush.ticketing.global.config;

import com.aquarush.ticketing.ratelimit.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 설정
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * 인터셉터 등록
     *
     * 인터셉터란?
     * - 컨트롤러 실행 전/후에 공통 로직 실행
     * - 예: 인증, 로깅, 유량제어
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")          // API 경로에만 적용
                .excludePathPatterns(
                        "/api/health",               // 헬스체크 제외
                        "/api/swagger-ui/**",        // Swagger 제외
                        "/api/v3/api-docs/**"        // API 문서 제외
                );
    }
}
