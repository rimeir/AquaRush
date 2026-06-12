package com.aquarush.ticketing.reservation.service;

import com.aquarush.ticketing.lock.service.DistributedLockService;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.dto.ReservationResponse;
import com.aquarush.ticketing.reservation.entity.Reservation;
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
 * 예약 생성 시 Redisson 락이 트랜잭션 전체를 감싸도록 ReservationTxService에 위임한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final DistributedLockService distributedLockService;
    private final WaitingQueueService waitingQueueService;
    private final ReservationTxService reservationTxService;

    /**
     * 예약 생성 (분산 락 → 트랜잭션 순서 보장)
     *
     * Redisson 락 획득 후 ReservationTxService를 호출하여 트랜잭션을 시작함으로써
     * 락 해제가 트랜잭션 커밋 이후에 일어나는 것을 보장한다.
     */
    public ReservationResponse createReservation(ReservationCreateRequest request) {
        log.info("예약 생성 시도: courseId={}, userId={}",
                request.getCourseId(), request.getUserId());

        if (request.getSessionId() != null) {
            enterQueueIfAbsent(request.getSessionId(), request.getCourseId());
            checkQueueAllowed(request.getSessionId(), request.getCourseId());
        }

        String lockKey = "reservation:course:" + request.getCourseId();

        // 락 획득 → 트랜잭션 시작(TxService) → 커밋 → 락 해제
        ReservationResponse response = distributedLockService.executeWithLock(
                lockKey,
                5L,
                10L,
                () -> reservationTxService.createWithTransaction(request)
        );

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