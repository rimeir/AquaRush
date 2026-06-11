package com.aquarush.ticketing.simulation.service;

import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.service.ReservationService;
import com.aquarush.ticketing.simulation.entity.VirtualUser;
import com.aquarush.ticketing.waitingqueue.service.WaitingQueueService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 봇 시뮬레이션 서비스 (재시도 로직 추가)
 *
 * 주요 기능:
 * 1. N명의 봇 생성
 * 2. 멀티스레드로 동시 예약 시도
 * 3. 실패 시 재시도 (최대 5번)
 * 4. 성공/실패 통계 집계
 *
 * ⭐ 개선 사항:
 * - 재시도 로직 추가 (대기열에서 순서가 되면 성공)
 * - 재시도 간격 설정 (2초)
 * - 최대 재시도 횟수 설정 (5번)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private final VirtualUserService virtualUserService;
    private final ReservationService reservationService;
    private final WaitingQueueService waitingQueueService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MS = 2000;

    // 실행 중인 시뮬레이션의 중단 플래그 (simulationId → stopFlag)
    private final ConcurrentHashMap<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    /**
     * 봇 N명 생성
     *
     * @param count 생성할 봇 수
     * @return 생성된 봇 리스트
     *
     * 시간 복잡도: O(n)
     * - 1000명 생성 시 약 1초 소요
     */
    public List<VirtualUser> createBots(int count) {
        log.info("봇 생성 시작: {}명", count);

        List<VirtualUser> bots = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            VirtualUser bot = virtualUserService.createBot(i);
            bots.add(bot);

            // 100명 단위로 로그 출력 (너무 많은 로그 방지)
            if (i % 100 == 0) {
                log.info("봇 생성 진행 중: {}/{}", i, count);
            }
        }

        log.info("봇 생성 완료: {}명", count);
        return bots;
    }

    /**
     * 봇 시뮬레이션 시작 (재시도 로직 포함)
     *
     * @param courseId 강좌 ID
     * @param bots 봇 리스트
     * @return 시뮬레이션 결과
     *
     * 핵심 로직:
     * 1. 모든 봇이 동시에 예약 시도 (멀티스레드)
     * 2. 실패 시 자동 재시도 (최대 5번, 2초 간격)
     * 3. 성공/실패 카운트
     * 4. 결과 반환
     *
     * ⭐ 개선점:
     * - 대기열에서 "아직 차례가 안 됨" 응답을 받으면 재시도
     * - 정원이 다 차면 즉시 실패 처리
     * - 실제 티케팅처럼 계속 시도
     */
    public BotSimulationResult startBotSimulation(String simulationId, Long courseId, List<VirtualUser> bots) {
        log.info("봇 시뮬레이션 시작: simulationId={}, courseId={}, 봇 수={}", simulationId, courseId, bots.size());

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(simulationId, stopFlag);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);

        // 유저 포함 전체 참가자를 스레드 시작 전에 동시에 대기열 진입
        // → 모두 비슷한 timestamp를 가져 공정한 순위 경쟁
        for (VirtualUser participant : bots) {
            waitingQueueService.enterQueue(participant.getSessionId(), courseId);
        }
        log.info("✅ 전체 참가자 대기열 진입 완료: {}명", bots.size());

        int threadPoolSize = Math.min(bots.size(), 100);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(bots.size());

        String simKey = "simulation:" + simulationId;

        for (VirtualUser bot : bots) {
            executor.submit(() -> {
                try {
                    if (stopFlag.get()) {
                        failCount.incrementAndGet();
                        redisTemplate.opsForHash().increment(simKey, "failCount", 1L);
                        return;
                    }

                    // 봇별 랜덤 초기 딜레이 (5~60초) — 실제 사용자처럼 접속 후 강좌 탐색·클릭 시간 시뮬레이션
                    long initialDelay = ThreadLocalRandom.current().nextLong(5_000, 60_001);
                    Thread.sleep(initialDelay);

                    if (stopFlag.get()) {
                        failCount.incrementAndGet();
                        redisTemplate.opsForHash().increment(simKey, "failCount", 1L);
                        return;
                    }

                    boolean success = tryReservationWithRetry(
                            courseId,
                            bot,
                            totalAttempts,
                            stopFlag
                    );

                    if (success) {
                        successCount.incrementAndGet();
                        try { redisTemplate.opsForHash().increment(simKey, "successCount", 1L); }
                        catch (Exception ex) { log.warn("Redis successCount increment 실패: {}", ex.getMessage()); }
                        log.debug("✅ 봇 예약 성공: {}", bot.getNickname());
                    } else {
                        failCount.incrementAndGet();
                        try { redisTemplate.opsForHash().increment(simKey, "failCount", 1L); }
                        catch (Exception ex) { log.warn("Redis failCount increment 실패: {}", ex.getMessage()); }
                        log.debug("❌ 봇 예약 최종 실패: {}", bot.getNickname());
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                    try { redisTemplate.opsForHash().increment(simKey, "failCount", 1L); }
                    catch (Exception ex) { log.warn("Redis failCount increment 실패: {}", ex.getMessage()); }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    try { redisTemplate.opsForHash().increment(simKey, "failCount", 1L); }
                    catch (Exception ex) { log.warn("Redis failCount increment 실패: {}", ex.getMessage()); }
                    log.error("봇 예약 에러: botId={}", bot.getId(), e);

                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            // 모든 봇의 작업이 완료될 때까지 대기
            // 최대 10분 대기 (재시도 포함하므로 시간 증가)
            boolean completed = latch.await(10, TimeUnit.MINUTES);

            if (!completed) {
                log.warn("⚠️ 봇 시뮬레이션 타임아웃 발생");
            }

        } catch (InterruptedException e) {
            log.error("봇 시뮬레이션 중단됨", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            stopFlags.remove(simulationId);
        }

        log.info("🎯 봇 시뮬레이션 완료: 성공={}, 실패={}, 총 시도={}",
                successCount.get(), failCount.get(), totalAttempts.get());

        return BotSimulationResult.builder()
                .totalBots(bots.size())
                .successCount(successCount.get())
                .failCount(failCount.get())
                .totalAttempts(totalAttempts.get())
                .build();
    }

    /**
     * 재시도 로직이 포함된 예약 시도
     *
     * @param courseId 강좌 ID
     * @param bot 봇 사용자
     * @param totalAttempts 총 시도 횟수 카운터
     * @return 성공 여부
     *
     * 재시도 전략:
     * 1. 최대 5번 재시도
     * 2. 각 시도 사이에 2초 대기
     * 3. "대기 필요" 에러 → 재시도
     * 4. "정원 초과" 에러 → 즉시 실패
     * 5. "중복 예약" 에러 → 즉시 실패
     *
     * 실제 티케팅 시스템과 동일:
     * - 사용자가 F5(새로고침)를 누르는 것과 같음
     * - 대기열에서 순서가 되면 성공
     * - 정원이 다 차면 실패
     */
    private void removeFromQueueSilently(String sessionId, Long courseId) {
        try { waitingQueueService.removeFromQueue(sessionId, courseId); }
        catch (Exception e) { log.warn("대기열 제거 실패 (무시): sessionId={}", sessionId); }
    }

    private boolean tryReservationWithRetry(
            Long courseId,
            VirtualUser bot,
            AtomicInteger totalAttempts,
            AtomicBoolean stopFlag) {

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            if (stopFlag.get()) return false;
            totalAttempts.incrementAndGet();

            try {
                // 예약 시도
                ReservationCreateRequest request = ReservationCreateRequest.builder()
                        .courseId(courseId)
                        .userId(bot.getId())
                        .userName(bot.getNickname())
                        .userPhone("010-0000-0000")
                        .sessionId(bot.getSessionId())
                        .build();

                reservationService.createReservation(request);

                // 성공!
                if (attempt > 1) {
                    log.debug("🎉 봇 예약 성공 ({}번째 시도): {}",
                            attempt, bot.getNickname());
                }
                return true;

            } catch (IllegalStateException e) {
                // 예약 불가능한 상태 처리
                String errorMessage = e.getMessage();

                // 정원 초과 또는 예약 불가 → 대기열 제거 후 즉시 실패
                if (errorMessage.contains("정원") ||
                        errorMessage.contains("마감") ||
                        errorMessage.contains("불가") ||
                        errorMessage.contains("예약할 수 없는")) {

                    removeFromQueueSilently(bot.getSessionId(), courseId);
                    log.debug("⛔ 봇 예약 불가 (정원 초과): {}", bot.getNickname());
                    return false;
                }

                // 중복 예약 → 대기열 제거 후 즉시 실패
                if (errorMessage.contains("이미") || errorMessage.contains("중복")) {
                    removeFromQueueSilently(bot.getSessionId(), courseId);
                    log.debug("⛔ 봇 예약 불가 (중복): {}", bot.getNickname());
                    return false;
                }

                // 대기 필요 → 재시도
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.debug("⏳ 봇 예약 재시도 예정 ({}/{}): {}, 이유: {}",
                            attempt, MAX_RETRY_ATTEMPTS, bot.getNickname(), errorMessage);

                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }

                    if (stopFlag.get()) return false;
                } else {
                    // 최대 재시도 횟수 초과
                    log.debug("❌ 봇 예약 최종 실패 (재시도 초과): {}", bot.getNickname());
                    return false;
                }

            } catch (IllegalArgumentException e) {
                // 잘못된 요청 (courseId 없음 등) → 즉시 실패
                log.debug("⛔ 봇 예약 불가 (잘못된 요청): {}, 이유: {}",
                        bot.getNickname(), e.getMessage());
                return false;

            } catch (Exception e) {
                // 기타 예외 → 즉시 실패
                log.error("❌ 봇 예약 에러: {}", bot.getNickname(), e);
                return false;
            }
        }

        return false;
    }

    /**
     * 시뮬레이션 중단 신호 전송
     * 봇들은 다음 재시도 시점에 플래그를 확인하고 종료 (최대 2초 내)
     */
    public void stopBotSimulation(String simulationId) {
        AtomicBoolean flag = stopFlags.get(simulationId);
        if (flag != null) {
            flag.set(true);
            log.info("봇 시뮬레이션 중단 신호 전송: simulationId={}", simulationId);
        }
    }

    /**
     * 봇 시뮬레이션 결과 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class BotSimulationResult {
        private int totalBots;       // 총 봇 수
        private int successCount;    // 예약 성공 수
        private int failCount;       // 예약 실패 수
        private int totalAttempts;   // 총 시도 횟수 (재시도 포함)

        /**
         * 성공률 계산
         *
         * @return 성공률 (0.0 ~ 1.0)
         */
        public double getSuccessRate() {
            if (totalBots == 0) return 0.0;
            return (double) successCount / totalBots;
        }

        /**
         * 성공률 퍼센트 (0 ~ 100)
         */
        public double getSuccessPercentage() {
            return getSuccessRate() * 100;
        }

        /**
         * 평균 시도 횟수
         *
         * @return 봇 1명당 평균 시도 횟수
         */
        public double getAverageAttempts() {
            if (totalBots == 0) return 0.0;
            return (double) totalAttempts / totalBots;
        }

        @Override
        public String toString() {
            return String.format(
                    "BotSimulationResult{총 봇=%d, 성공=%d, 실패=%d, 성공률=%.2f%%, 총 시도=%d, 평균 시도=%.2f}",
                    totalBots, successCount, failCount, getSuccessPercentage(),
                    totalAttempts, getAverageAttempts()
            );
        }
    }
}