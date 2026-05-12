package com.aquarush.ticketing.reservation.service;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import com.aquarush.ticketing.lock.service.DistributedLockService;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.dto.ReservationResponse;
import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.entity.ReservationStatus;
import com.aquarush.ticketing.reservation.repository.ReservationRepository;
import com.aquarush.ticketing.waitingqueue.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 예약 서비스
 *
 * ⭐ 개선 사항:
 * - findById() → findByIdWithLock() 변경
 * - 비관적 락으로 완벽한 동시성 제어
 * - 정원 초과 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CourseRepository courseRepository;
    private final DistributedLockService distributedLockService;
    private final WaitingQueueService waitingQueueService;

    /**
     * 예약 생성 (분산 락 + 비관적 락 적용)
     *
     * ⭐ 변경 사항:
     * - courseRepository.findById() → findByIdWithLock()
     * - DB 레벨 잠금으로 완벽한 동시성 제어
     * - 정원 초과 완벽 방지
     */
    @Transactional
    public ReservationResponse createReservation(ReservationCreateRequest request) {
        log.info("예약 생성 시도: courseId={}, userId={}",
                request.getCourseId(), request.getUserId());

        // 대기열 게이트: sessionId가 있는 경우 대기열을 통해서만 예약 가능
        if (request.getSessionId() != null) {
            enterQueueIfAbsent(request.getSessionId(), request.getCourseId());
            checkQueueAllowed(request.getSessionId(), request.getCourseId());
        }

        // 분산 락 사용
        String lockKey = "reservation:course:" + request.getCourseId();

        ReservationResponse response = distributedLockService.executeWithLock(
                lockKey,
                5L,   // 5초 대기
                10L,  // 10초 유지
                () -> {
                    // 1. ⭐ 강좌 조회 (비관적 락)
                    // Before: courseRepository.findById(request.getCourseId())
                    // After:  courseRepository.findByIdWithLock(request.getCourseId())
                    Course course = courseRepository.findByIdWithLock(request.getCourseId())
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "강좌를 찾을 수 없습니다: " + request.getCourseId()));

                    log.debug("강좌 조회 (LOCK): courseId={}, capacity={}/{}",
                            course.getId(),
                            course.getCurrentCapacity(),
                            course.getMaxCapacity());

                    // 2. 예약 가능 여부 확인
                    if (!course.isAvailable()) {
                        log.warn("❌ 예약 불가: courseId={}, capacity={}/{}, status={}",
                                course.getId(),
                                course.getCurrentCapacity(),
                                course.getMaxCapacity(),
                                course.getStatus());
                        throw new IllegalStateException("예약할 수 없는 강좌입니다.");
                    }

                    // 3. 중복 예약 확인
                    boolean exists = reservationRepository.existsActiveByCourseIdAndUserId(
                            request.getCourseId(),
                            request.getUserId()
                    );

                    if (exists) {
                        log.warn("❌ 중복 예약: courseId={}, userId={}",
                                request.getCourseId(),
                                request.getUserId());
                        throw new IllegalStateException("이미 예약한 강좌입니다.");
                    }

                    // 4. 예약 생성
                    Reservation reservation = Reservation.builder()
                            .course(course)
                            .userId(request.getUserId())
                            .userName(request.getUserName())
                            .userPhone(request.getUserPhone())
                            .status(ReservationStatus.CONFIRMED)
                            .build();

                    reservationRepository.save(reservation);

                    // 5. 정원 증가
                    course.increaseCapacity();

                    log.info("✅ 예약 성공: id={}, courseId={}, userId={}, capacity={}/{}",
                            reservation.getId(),
                            course.getId(),
                            request.getUserId(),
                            course.getCurrentCapacity(),
                            course.getMaxCapacity());

                    // 6. 응답 반환
                    return ReservationResponse.from(reservation);
                }
        );

        // 예약 성공 후 대기열에서 제거
        if (request.getSessionId() != null) {
            waitingQueueService.removeFromQueue(request.getSessionId(), request.getCourseId());
        }

        return response;
    }

    private void enterQueueIfAbsent(String sessionId, Long courseId) {
        if (waitingQueueService.getQueuePosition(sessionId, courseId) == null) {
            waitingQueueService.enterQueue(sessionId, courseId);
            log.debug("대기열 진입: sessionId={}, courseId={}", sessionId, courseId);
        }
    }

    private void checkQueueAllowed(String sessionId, Long courseId) {
        if (!waitingQueueService.isAllowedToReserve(sessionId, courseId)) {
            Long rank = waitingQueueService.getQueuePosition(sessionId, courseId);
            throw new IllegalStateException(
                    String.format("대기 중입니다. 현재 순번: %d", rank));
        }
    }

    /**
     * 예약 상세 조회
     */
    public ReservationResponse getReservation(Long id) {
        Reservation reservation = reservationRepository.findByIdWithCourse(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "예약을 찾을 수 없습니다: " + id));

        return ReservationResponse.from(reservation);
    }

    /**
     * 사용자의 예약 목록 조회
     */
    public List<ReservationResponse> getMyReservations(Long userId) {
        List<Reservation> reservations = reservationRepository.findByUserIdWithCourse(userId);

        return reservations.stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 강좌별 예약 목록 조회
     */
    public List<ReservationResponse> getCourseReservations(Long courseId) {
        List<Reservation> reservations = reservationRepository.findByCourseId(courseId);

        return reservations.stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 예약 취소
     */
    @Transactional
    public void cancelReservation(Long id, String reason) {
        log.info("예약 취소 시도: id={}", id);

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "예약을 찾을 수 없습니다: " + id));

        // 취소 가능 여부 확인
        if (!reservation.isCancellable()) {
            throw new IllegalStateException("취소할 수 없는 예약입니다.");
        }

        // 예약 취소
        reservation.cancel(reason);

        // 정원 감소
        reservation.getCourse().decreaseCapacity();

        log.info("예약 취소 완료: id={}, courseId={}",
                reservation.getId(), reservation.getCourse().getId());
    }
}