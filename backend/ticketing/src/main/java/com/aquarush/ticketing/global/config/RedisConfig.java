package com.aquarush.ticketing.global.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 클래스
 *
 * 역할:
 * 1. Redis 연결 팩토리 생성
 * 2. RedisTemplate 설정 (데이터 저장/조회)
 * 3. RedissonClient 설정 (분산 락)
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    /**
     * Redis 연결 팩토리
     *
     * Lettuce를 사용하는 이유:
     * - Thread-safe: 여러 스레드가 동시에 사용 가능
     * - Netty 기반: 비동기 처리로 성능 우수
     * - Connection Pooling: 연결 재사용으로 효율적
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(host, port);
    }

    /**
     * RedisTemplate 설정
     *
     * 직렬화(Serialization)란?
     * - 객체 → 바이트 변환: 저장/전송 가능한 형태로 변환
     * - 바이트 → 객체 복원: 저장된 데이터를 다시 객체로 변환
     *
     * 왜 직렬화가 필요한가?
     * - Redis는 바이트만 저장 가능
     * - Java 객체를 그대로 저장할 수 없음
     * - JSON 형태로 변환하여 저장
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());

        // Key는 String으로 직렬화
        // 예: "user:1", "course:123" 형태
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로 직렬화
        // 예: {"name":"홍길동","age":25}
        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * RedissonClient 설정
     *
     * Redisson의 역할:
     * 1. 분산 락(Distributed Lock) 제공
     * 2. 멀티 서버 환경에서 동시성 제어
     * 3. Pub/Sub 메시징
     *
     * 예시:
     * - 여러 서버에서 동시에 같은 좌석 예약 시도
     * - 분산 락으로 한 번에 한 서버만 처리
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // Single Server 모드 설정
        // 실제 운영 환경에서는 Cluster 모드 권장
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(64)           // 연결 풀 크기
                .setConnectionMinimumIdleSize(24)    // 최소 유휴 연결
                .setIdleConnectionTimeout(10000)     // 유휴 연결 타임아웃
                .setConnectTimeout(3000)             // 연결 타임아웃
                .setTimeout(3000)                    // 응답 타임아웃
                .setRetryAttempts(3)                 // 재시도 횟수
                .setRetryInterval(1500);             // 재시도 간격

        return Redisson.create(config);
    }
}
