package com.aquarush.ticketing.reservation.service;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.dto.ReservationResponse;
import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.entity.ReservationStatus;
import com.aquarush.ticketing.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationTxService {

    private final CourseRepository courseRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 예약 생성 트랜잭션 (Redisson 락 내부에서 호출)
     *
     * 호출 전 Redisson 락이 반드시 획득된 상태여야 하며,
     * 이 메서드가 반환(커밋)된 이후 락이 해제됨으로써
     * 락이 트랜잭션 전체를 감싸는 구조가 보장된다.
     */
    @Transactional
    public ReservationResponse createWithTransaction(ReservationCreateRequest request) {
        Course course = courseRepository.findByIdWithLock(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "강좌를 찾을 수 없습니다: " + request.getCourseId()));

        log.debug("강좌 조회 (LOCK): courseId={}, capacity={}/{}",
                course.getId(), course.getCurrentCapacity(), course.getMaxCapacity());

        if (!course.isAvailable()) {
            log.warn("❌ 예약 불가: courseId={}, capacity={}/{}, status={}",
                    course.getId(), course.getCurrentCapacity(),
                    course.getMaxCapacity(), course.getStatus());
            throw new IllegalStateException("예약할 수 없는 강좌입니다.");
        }

        boolean exists = reservationRepository.existsActiveByCourseIdAndUserId(
                request.getCourseId(), request.getUserId());
        if (exists) {
            log.warn("❌ 중복 예약: courseId={}, userId={}",
                    request.getCourseId(), request.getUserId());
            throw new IllegalStateException("이미 예약한 강좌입니다.");
        }

        Reservation reservation = Reservation.builder()
                .course(course)
                .userId(request.getUserId())
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationRepository.save(reservation);
        course.increaseCapacity();

        log.info("✅ 예약 성공: id={}, courseId={}, userId={}, capacity={}/{}",
                reservation.getId(), course.getId(), request.getUserId(),
                course.getCurrentCapacity(), course.getMaxCapacity());

        return ReservationResponse.from(reservation);
    }
}
