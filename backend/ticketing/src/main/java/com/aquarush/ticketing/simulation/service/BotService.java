package com.aquarush.ticketing.simulation.service;

import com.aquarush.ticketing.accessqueue.service.AccessQueueService;
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
 * True Method B + 페이지 로드/9시 분리 진입
 *
 * [페이지 로드 시] enterAccessQueue()에서 earlyBotCount 봇 미리 진입
 *   → 9시 전에도 오버레이에서 "N번째 대기 중" 표시
 *
 * [9시 시뮬레이션 시작 시]
 *   earlyBot(botIdx < earlyBotCount): poll → 슬롯 제거 대기 후 예약 경쟁
 *   lateBot 80%: 즉시 enterBotQueue(userVirtualMs - offset) → 유저 순번 상승
 *   lateBot 20%: 0~3초 딜레이 후 enterBotQueue(userVirtualMs + offset) → "내 뒤에 N명" 증가
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotService {

    private final VirtualUserService virtualUserService;
    private final ReservationService reservationService;
    private final WaitingQueueService waitingQueueService;
    private final AccessQueueService accessQueueService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final int  MAX_RETRY_ATTEMPTS  = 5;
    private static final long RETRY_DELAY_MS      = 2000;
    private static final long LATE_BOT_TIME_SCALE = 10L;

    private final ConcurrentHashMap<String, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

    public List<VirtualUser> createBots(int count) {
        log.info("봇 생성 시작: {}명", count);
        List<VirtualUser> bots = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            bots.add(virtualUserService.createBot(i));
            if (i % 100 == 0) log.info("봇 생성 진행 중: {}/{}", i, count);
        }
        log.info("봇 생성 완료: {}명", count);
        return bots;
    }

    public BotSimulationResult startBotSimulation(
            String simulationId, Long courseId, List<VirtualUser> bots, String queueToken) {

        log.info("봇 시뮬레이션 시작: simulationId={}, 봇 수={}, queueToken={}",
                simulationId, bots.size(), queueToken);

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        stopFlags.put(simulationId, stopFlag);

        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger failCount     = new AtomicInteger(0);
        AtomicInteger totalAttempts = new AtomicInteger(0);

        for (VirtualUser bot : bots) {
            waitingQueueService.enterQueue(bot.getSessionId(), courseId);
        }

        // 페이지 로드 시 미리 진입한 봇 수 (bot:0 ~ bot:earlyBotCount-1 이 큐에 있음)
        final int earlyBotCount = (queueToken != null && !queueToken.isBlank())
                ? accessQueueService.getEarlyBotCount(queueToken)
                : 0;

        // 9시: 처리 활성화 — 스케줄러가 큐 드레인 시작
        if (queueToken != null && !queueToken.isBlank()) {
            accessQueueService.enableProcessing(queueToken);
        }

        // 나머지 봇(late) 중 80%: 유저보다 앞 타임스탬프 → 순번 상승
        //                    20%: 0~3초 딜레이 후 유저보다 뒤 타임스탬프 → "내 뒤에 N명" 증가
        final int lateBotCount     = bots.size() - earlyBotCount;
        final int lateBotAheadCount = (int)(lateBotCount * 0.80);
        final long userVirtualMs = (queueToken != null && !queueToken.isBlank())
                ? accessQueueService.getUserVirtualMs(queueToken)
                : 0L;

        // ── 봇 스레드 시작 ────────────────────────────────────────────────────
        int threadPoolSize = Math.min(bots.size(), 100);
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch latch = new CountDownLatch(bots.size());
        String simKey = "simulation:" + simulationId;

        for (int i = 0; i < bots.size(); i++) {
            final int botIdx = i;
            final VirtualUser bot = bots.get(i);
            final boolean inQueue    = queueToken != null && !queueToken.isBlank() && botIdx < earlyBotCount;
            final boolean isLateAhead = !inQueue && queueToken != null && !queueToken.isBlank()
                    && (botIdx - earlyBotCount) < lateBotAheadCount;

            executor.submit(() -> {
                try {
                    if (stopFlag.get()) { fail(simKey, failCount); return; }

                    if (inQueue) {
                        // earlyBot: 슬롯 제거(입장 허가)될 때까지 폴링
                        while (accessQueueService.isBotInQueue(queueToken, botIdx)) {
                            if (stopFlag.get()) { fail(simKey, failCount); return; }
                            Thread.sleep(500);
                        }
                        log.debug("봇 대기열 통과: bot:{}", botIdx);
                    } else if (queueToken != null && !queueToken.isBlank()) {
                        // lateBot: 큐에 진입하여 유저 순번/후방 카운트에 영향
                        if (isLateAhead) {
                            long offset = ThreadLocalRandom.current().nextLong(1, LATE_BOT_TIME_SCALE * 1_000 + 1);
                            accessQueueService.enterBotQueue(queueToken, botIdx, userVirtualMs - offset);
                        } else {
                            long delay = ThreadLocalRandom.current().nextLong(0, 3_001);
                            Thread.sleep(delay);
                            if (stopFlag.get()) { fail(simKey, failCount); return; }
                            long offset = ThreadLocalRandom.current().nextLong(1, LATE_BOT_TIME_SCALE * 1_000 + 1);
                            accessQueueService.enterBotQueue(queueToken, botIdx, userVirtualMs + offset);
                        }
                    }

                    if (stopFlag.get()) { fail(simKey, failCount); return; }

                    Thread.sleep(ThreadLocalRandom.current().nextLong(5_000, 60_001));

                    if (stopFlag.get()) { fail(simKey, failCount); return; }

                    if (tryReservationWithRetry(courseId, bot, totalAttempts, stopFlag)) {
                        successCount.incrementAndGet();
                        try { redisTemplate.opsForHash().increment(simKey, "successCount", 1L); } catch (Exception ignored) {}
                    } else {
                        failCount.incrementAndGet();
                        try { redisTemplate.opsForHash().increment(simKey, "failCount", 1L); } catch (Exception ignored) {}
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    fail(simKey, failCount);
                } catch (Exception e) {
                    fail(simKey, failCount);
                    log.error("봇 예약 에러: botId={}", bot.getId(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            if (!latch.await(10, TimeUnit.MINUTES)) log.warn("봇 시뮬레이션 타임아웃");
        } catch (InterruptedException e) {
            log.error("봇 시뮬레이션 중단됨", e);
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            stopFlags.remove(simulationId);
        }

        log.info("봇 시뮬레이션 완료: 성공={}, 실패={}, 총 시도={}",
                successCount.get(), failCount.get(), totalAttempts.get());

        return BotSimulationResult.builder()
                .totalBots(bots.size())
                .successCount(successCount.get())
                .failCount(failCount.get())
                .totalAttempts(totalAttempts.get())
                .build();
    }

    public void stopBotSimulation(String simulationId) {
        AtomicBoolean flag = stopFlags.get(simulationId);
        if (flag != null) {
            flag.set(true);
            log.info("봇 시뮬레이션 중단 신호 전송: simulationId={}", simulationId);
        }
    }

    private void fail(String simKey, AtomicInteger failCount) {
        failCount.incrementAndGet();
        try { redisTemplate.opsForHash().increment(simKey, "failCount", 1L); } catch (Exception ignored) {}
    }

    private void removeFromQueueSilently(String sessionId, Long courseId) {
        try { waitingQueueService.removeFromQueue(sessionId, courseId); }
        catch (Exception e) { log.warn("대기열 제거 실패: sessionId={}", sessionId); }
    }

    private boolean tryReservationWithRetry(
            Long courseId, VirtualUser bot,
            AtomicInteger totalAttempts, AtomicBoolean stopFlag) {

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            if (stopFlag.get()) return false;
            totalAttempts.incrementAndGet();

            try {
                reservationService.createReservation(ReservationCreateRequest.builder()
                        .courseId(courseId).userId(bot.getId()).userName(bot.getNickname())
                        .userPhone("010-0000-0000").sessionId(bot.getSessionId()).build());
                return true;
            } catch (IllegalStateException e) {
                String msg = e.getMessage();
                if (msg.contains("정원") || msg.contains("마감") || msg.contains("불가") || msg.contains("예약할 수 없는")) {
                    removeFromQueueSilently(bot.getSessionId(), courseId); return false;
                }
                if (msg.contains("이미") || msg.contains("중복")) {
                    removeFromQueueSilently(bot.getSessionId(), courseId); return false;
                }
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
                    if (stopFlag.get()) return false;
                }
            } catch (IllegalArgumentException e) {
                return false;
            } catch (Exception e) {
                log.error("봇 예약 에러: {}", bot.getNickname(), e); return false;
            }
        }
        return false;
    }

    @Getter @Builder @AllArgsConstructor
    public static class BotSimulationResult {
        private int totalBots;
        private int successCount;
        private int failCount;
        private int totalAttempts;

        public double getSuccessRate()       { return totalBots == 0 ? 0.0 : (double) successCount / totalBots; }
        public double getSuccessPercentage() { return getSuccessRate() * 100; }
        public double getAverageAttempts()   { return totalBots == 0 ? 0.0 : (double) totalAttempts / totalBots; }
    }
}
