package com.aquarush.ticketing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisBasicTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void Redis_기본_동작_테스트() {
        // given
        String key = "test:key";
        String value = "Hello Redis!";

        // when
        redisTemplate.opsForValue().set(key, value);
        Object result = redisTemplate.opsForValue().get(key);

        // then
        assertThat(result).isEqualTo(value);

        // cleanup
        redisTemplate.delete(key);
    }
}

