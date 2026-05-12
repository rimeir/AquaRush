package com.aquarush.ticketing.lock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    /**
     * 락 획득 및 작업 실행
     *
     * @param lockKey 락 키
     * @param waitTime 대기 시간 (초)
     * @param leaseTime 락 유지 시간 (초)
     * @param task 실행할 작업
     * @return 작업 결과
     */
    public <T> T executeWithLock(
            String lockKey,
            long waitTime,
            long leaseTime,
            LockTask<T> task
    ) {
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!acquired) {
                log.warn("락 획득 실패: lockKey={}", lockKey);
                throw new IllegalStateException("락을 획득할 수 없습니다. 잠시 후 다시 시도해주세요.");
            }

            log.debug("락 획득 성공: lockKey={}", lockKey);

            // 작업 실행
            return task.execute();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("락 획득 중 인터럽트: lockKey={}", lockKey);
            throw new RuntimeException("작업이 중단되었습니다.", e);

        } finally {
            // 락 해제
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("락 해제: lockKey={}", lockKey);
            }
        }
    }

    /**
     * 락을 사용하는 작업 인터페이스
     */
    @FunctionalInterface
    public interface LockTask<T> {
        T execute();
    }
}