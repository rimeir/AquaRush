package com.aquarush.ticketing.simulation.service;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.repository.ReservationRepository;
import com.aquarush.ticketing.simulation.dto.SimulationStatusResponse;
import com.aquarush.ticketing.simulation.entity.VirtualUser;
import com.aquarush.ticketing.waitingqueue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시뮬레이션 서비스 (임시 해결 버전)
 *
 * ⭐ 변경 사항:
 * - deleteByCourseId() 대신 findByCourseId() + deleteAll() 사용
 * - clearQueue() 호출 제거 (메서드 미구현)
 *
 * 나중에 ReservationRepository와 WaitingQueueService에
 * 해당 메서드를 추가한 후 원래 코드로 복원하세요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final CourseRepository courseRepository;
    private final ReservationRepository reservationRepository;
    private final BotService botService;
    private final VirtualUserService virtualUserService;
    private final WaitingQueueService waitingQueueService;
    private final RedisTemplate<String, Object> redisTemplate;

    // SSE Emitter 저장소
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 시뮬레이션 생성 (강좌 자동 초기화 포함)
     */
    @Transactional
    public String createSimulation(Long courseId, VirtualUser user, List<VirtualUser> bots,
                                   Integer totalSeats, Integer remainingSeats) {
        String simulationId = UUID.randomUUID().toString();

        // ⭐ 시뮬레이션 시작 전 강좌 초기화
        resetCourseForSimulation(courseId, totalSeats, remainingSeats);

        // Redis에 시뮬레이션 정보 저장
        String key = "simulation:" + simulationId;
        redisTemplate.opsForHash().put(key, "courseId", courseId.toString());
        redisTemplate.opsForHash().put(key, "userId", user.getSessionId());
        redisTemplate.opsForHash().put(key, "userDbId", user.getId().toString());
        redisTemplate.opsForHash().put(key, "nickname", user.getNickname());
        redisTemplate.opsForHash().put(key, "botCount", String.valueOf(bots.size()));
        redisTemplate.opsForHash().put(key, "status", "RUNNING");
        redisTemplate.opsForHash().put(key, "successCount", 0L);
        redisTemplate.opsForHash().put(key, "failCount", 0L);
        redisTemplate.opsForHash().put(key, "totalAttempts", 0L);

        // 유저를 대기열에 자동 진입
        waitingQueueService.enterQueue(user.getSessionId(), courseId);
        log.info("✅ 시뮬레이션 생성: id={}, courseId={}, botCount={}, userSessionId={}",
                simulationId, courseId, bots.size(), user.getSessionId());

        return simulationId;
    }

    @Transactional
    public void resetCourseForSimulation(Long courseId, Integer totalSeats, Integer remainingSeats) {
        try {
            log.info("🔄 시뮬레이션을 위한 강좌 초기화 시작: courseId={}", courseId);

            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new IllegalArgumentException("강좌를 찾을 수 없습니다: " + courseId));

            List<Reservation> reservations = reservationRepository.findByCourseId(courseId);
            if (!reservations.isEmpty()) {
                reservationRepository.deleteAll(reservations);
                log.info("📝 기존 예약 삭제: count={}", reservations.size());
            }

            if (totalSeats != null && remainingSeats != null) {
                course.setupForSimulation(totalSeats, remainingSeats);
                log.info("✅ 사용자 설정 정원 적용: {}석 중 {}석 남음", totalSeats, remainingSeats);
            } else {
                course.resetCapacity();
            }
            courseRepository.save(course);

            waitingQueueService.clearQueue(courseId);

            log.info("✅ 강좌 초기화 완료: courseId={}, capacity={}/{}", courseId,
                    course.getCurrentCapacity(), course.getMaxCapacity());

        } catch (Exception e) {
            log.error("❌ 강좌 초기화 실패: courseId={}", courseId, e);
            throw new RuntimeException("강좌 초기화 중 오류 발생", e);
        }
    }

    /**
     * 봇 시뮬레이션 비동기 시작 (봇 정리 로직 포함)
     */
    @Async
    public void startBotSimulation(String simulationId, Long courseId, List<VirtualUser> bots) {
        log.info("🚀 봇 시뮬레이션 비동기 시작: simulationId={}, botCount={}",
                simulationId, bots.size());

        String key = "simulation:" + simulationId;

        try {
            // 봇 시뮬레이션 실행
            BotService.BotSimulationResult result =
                    botService.startBotSimulation(simulationId, courseId, bots);

            // 결과 저장
            redisTemplate.opsForHash().put(key, "successCount",
                    String.valueOf(result.getSuccessCount()));
            redisTemplate.opsForHash().put(key, "failCount",
                    String.valueOf(result.getFailCount()));
            redisTemplate.opsForHash().put(key, "totalAttempts",
                    String.valueOf(result.getTotalAttempts()));
            redisTemplate.opsForHash().put(key, "status", "COMPLETED");

            log.info("✅ 봇 시뮬레이션 완료: simulationId={}, 성공={}, 실패={}, 총시도={}",
                    simulationId, result.getSuccessCount(), result.getFailCount(),
                    result.getTotalAttempts());

        } catch (Exception e) {
            log.error("❌ 봇 시뮬레이션 실패: simulationId={}", simulationId, e);

            // 실패 상태 저장
            redisTemplate.opsForHash().put(key, "status", "FAILED");
            redisTemplate.opsForHash().put(key, "errorMessage", e.getMessage());

        } finally {
            // 봇 정리 (성공/실패 관계없이)
            cleanupBots(simulationId, bots);

            // 대기열 정리
            waitingQueueService.clearQueue(courseId);
            log.info("대기열 초기화 완료: simulationId={}, courseId={}", simulationId, courseId);

            // SSE 연결 종료
            closeEmitter(simulationId);
        }
    }

    /**
     * 봇 정리 (시뮬레이션 종료 시)
     */
    private void cleanupBots(String simulationId, List<VirtualUser> bots) {
        try {
            log.info("🧹 봇 정리 시작: simulationId={}, botCount={}",
                    simulationId, bots.size());

            // 봇 삭제
            virtualUserService.deleteBots(bots);

            log.info("✅ 봇 정리 완료: simulationId={}", simulationId);

        } catch (Exception e) {
            log.error("❌ 봇 정리 중 오류 발생: simulationId={}", simulationId, e);
            // 정리 실패해도 계속 진행
        }
    }

    /**
     * SSE Emitter 종료
     */
    private void closeEmitter(String simulationId) {
        SseEmitter emitter = emitters.get(simulationId);
        if (emitter != null) {
            try {
                // 마지막 상태 전송
                SimulationStatusResponse finalStatus = getStatus(simulationId);
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(finalStatus));

                // 완료 이벤트 전송
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("시뮬레이션이 완료되었습니다."));

                emitter.complete();
                log.info("✅ SSE 연결 정상 종료: simulationId={}", simulationId);

            } catch (Exception e) {
                log.warn("⚠️ SSE 종료 중 오류: simulationId={}", simulationId);
                emitter.completeWithError(e);
            } finally {
                emitters.remove(simulationId);
            }
        }
    }

    /**
     * 시뮬레이션 현황 조회
     */
    public SimulationStatusResponse getStatus(String simulationId) {
        String key = "simulation:" + simulationId;

        // Redis에서 정보 조회
        Object courseIdObj = redisTemplate.opsForHash().get(key, "courseId");
        Object userIdObj = redisTemplate.opsForHash().get(key, "userId");
        Object userDbIdObj = redisTemplate.opsForHash().get(key, "userDbId");
        Object nicknameObj = redisTemplate.opsForHash().get(key, "nickname");
        Object botCountObj = redisTemplate.opsForHash().get(key, "botCount");
        Object statusObj = redisTemplate.opsForHash().get(key, "status");
        Object successCountObj = redisTemplate.opsForHash().get(key, "successCount");
        Object failCountObj = redisTemplate.opsForHash().get(key, "failCount");

        if (courseIdObj == null) {
            throw new IllegalArgumentException("시뮬레이션을 찾을 수 없습니다: " + simulationId);
        }

        Long courseId = Long.parseLong(courseIdObj.toString());
        String userId = userIdObj.toString();
        Long userDbId = userDbIdObj != null ? Long.parseLong(userDbIdObj.toString()) : null;
        String nickname = nicknameObj.toString();
        int botCount = Integer.parseInt(botCountObj.toString());
        String status = statusObj.toString();
        int successCount = Integer.parseInt(successCountObj.toString());
        int failCount = Integer.parseInt(failCountObj.toString());

        // 강좌 정보 조회
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("강좌를 찾을 수 없습니다."));

        // 대기열 정보 (null 체크)
        Long queueLength = waitingQueueService.getQueueLength(courseId);
        Long myRank = waitingQueueService.getQueuePosition(userId, courseId);

        // 예상 대기 시간 (null 체크)
        int estimatedWaitTime = myRank != null ? (int) Math.ceil((double) myRank / 10) : 0;

        // 남은 좌석 계산 (안전하게)
        int remainingSeats = Math.max(0, course.getMaxCapacity() - course.getCurrentCapacity());

        // 유저 예약 성공 여부 및 순위
        boolean myReservationSuccess = userDbId != null &&
                reservationRepository.existsActiveByCourseIdAndUserId(courseId, userDbId);
        Integer myPosition = null;
        if (myReservationSuccess) {
            long before = reservationRepository.countReservationsBeforeUser(courseId, userDbId);
            myPosition = (int) before + 1;
        }

        return SimulationStatusResponse.builder()
                .simulationId(simulationId)
                .courseId(courseId)
                .courseName(course.getName())
                .centerName(course.getCenterName())
                .weekdays(course.getWeekdays())
                .timeSlot(course.getTimeSlot())
                .level(course.getLevel())
                .targetAudience(course.getTargetAudience())
                .myNickname(nickname)
                .status(status)
                .totalParticipants(botCount + 1)
                .successCount(successCount)
                .failCount(failCount)
                .totalSeats(course.getMaxCapacity())
                .remainingSeats(remainingSeats)
                .queueLength(queueLength != null ? queueLength : 0L)
                .myRank(myRank)
                .estimatedWaitTime(estimatedWaitTime)
                .myReservationSuccess(myReservationSuccess)
                .myPosition(myPosition)
                .build();
    }

    /**
     * 실시간 통계 스트림 (SSE)
     */
    public SseEmitter streamStatus(String simulationId) {
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃

        // Emitter 저장
        emitters.put(simulationId, emitter);

        // 완료/타임아웃 시 제거
        emitter.onCompletion(() -> {
            log.info("SSE 연결 완료: simulationId={}", simulationId);
            emitters.remove(simulationId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE 타임아웃: simulationId={}", simulationId);
            emitters.remove(simulationId);
        });

        emitter.onError(e -> {
            log.error("SSE 오류: simulationId={}, error={}", simulationId, e.getMessage());
            emitters.remove(simulationId);
        });

        // 초기 데이터 전송
        try {
            SimulationStatusResponse status = getStatus(simulationId);
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(status));

            log.debug("SSE 초기 데이터 전송 완료: simulationId={}", simulationId);

        } catch (IOException e) {
            log.error("초기 데이터 전송 실패: simulationId={}", simulationId, e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * 모든 연결된 클라이언트에게 업데이트 전송
     */
    public void broadcastUpdate(String simulationId) {
        SseEmitter emitter = emitters.get(simulationId);
        if (emitter == null) {
            return;
        }

        try {
            SimulationStatusResponse status = getStatus(simulationId);
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(status));

            log.trace("SSE 업데이트 전송 완료: simulationId={}", simulationId);

        } catch (IOException e) {
            log.error("SSE 전송 실패: simulationId={}, error={}", simulationId, e.getMessage());
            emitter.completeWithError(e);
            emitters.remove(simulationId);
        }
    }

    /**
     * 시뮬레이션 종료
     * 봇 중단 → 상태 STOPPED → SSE 즉시 종료
     */
    public SimulationStatusResponse stopSimulation(String simulationId) {
        String key = "simulation:" + simulationId;

        if (redisTemplate.opsForHash().get(key, "status") == null) {
            throw new IllegalArgumentException("시뮬레이션을 찾을 수 없습니다: " + simulationId);
        }

        // 봇 중단 플래그 설정 (최대 2초 내 봇 스레드 종료)
        botService.stopBotSimulation(simulationId);

        // 대기열 정리
        Long courseId = Long.parseLong(redisTemplate.opsForHash().get(key, "courseId").toString());
        waitingQueueService.clearQueue(courseId);

        // 상태 변경
        redisTemplate.opsForHash().put(key, "status", "STOPPED");

        // SSE 즉시 종료 (emitters 맵에서 제거하여 async 태스크의 closeEmitter가 중복 실행되지 않도록)
        SseEmitter emitter = emitters.remove(simulationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("stopped").data("시뮬레이션이 종료되었습니다."));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }

        log.info("시뮬레이션 종료: id={}", simulationId);
        return getStatus(simulationId);
    }

    /**
     * 시뮬레이션 삭제
     */
    public void deleteSimulation(String simulationId) {
        String key = "simulation:" + simulationId;
        redisTemplate.delete(key);

        // SSE 연결 종료
        SseEmitter emitter = emitters.remove(simulationId);
        if (emitter != null) {
            emitter.complete();
        }

        log.info("시뮬레이션 삭제: id={}", simulationId);
    }
}