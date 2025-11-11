package com.aquarush.ticketing.reservation.service;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import com.aquarush.ticketing.reservation.dto.ReservationCreateRequest;
import com.aquarush.ticketing.reservation.dto.ReservationDetailResponse;
import com.aquarush.ticketing.reservation.dto.ReservationResponse;
import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.entity.ReservationStatus;
import com.aquarush.ticketing.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CourseRepository courseRepository;

    /**
     * 예약 생성
     * 강좌 존재 확인, 예약 가능 여부 확인, 중복 예약 확인 후 예약을 생성하고 정원을 증가시킵니다.
     */
    @Transactional
    public ReservationResponse createReservation(ReservationCreateRequest request) {
        log.info("예약 생성 시작 - courseId: {}, userId: {}",
                request.getCourseId(), request.getUserId());

        // 1. 강좌 조회
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "강좌를 찾을 수 없습니다. ID: " + request.getCourseId()));

        // 2. 강좌 예약 가능 여부 확인
        if (!course.isAvailable()) {
            throw new IllegalStateException("예약할 수 없는 강좌입니다. 상태: " + course.getStatus());
        }

        // 3. 중복 예약 확인
        boolean alreadyReserved = reservationRepository
                .existsByUserIdAndCourseIdAndActiveStatus(
                        request.getUserId(),
                        request.getCourseId());
        if (alreadyReserved) {
            throw new IllegalStateException("이미 예약한 강좌입니다.");
        }

        // 4. 예약 생성
        Reservation reservation = Reservation.builder()
                .course(course)
                .userId(request.getUserId())
                .userName(request.getUserName())
                .userPhone(request.getUserPhone())
                .userEmail(request.getUserEmail())
                .status(ReservationStatus.CONFIRMED)
                .build();

        reservationRepository.save(reservation);

        // 5. 강좌 정원 증가
        course.increaseCapacity();

        log.info("예약 생성 완료 - reservationId: {}", reservation.getId());

        return ReservationResponse.from(reservation);
    }

    /**
     * 예약 상세 조회
     * 예약 ID로 예약 정보를 조회하고 강좌 정보를 함께 반환합니다.
     */
    public ReservationDetailResponse getReservationDetail(Long reservationId) {
        log.info("예약 상세 조회 - reservationId: {}", reservationId);

        Reservation reservation = reservationRepository
                .findByIdWithCourse(reservationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "예약을 찾을 수 없습니다. ID: " + reservationId));

        return ReservationDetailResponse.from(reservation);
    }

    /**
     * 내 예약 목록 조회
     * 사용자 ID로 모든 예약을 조회하고 최근 예약 순으로 정렬합니다.
     */
    public List<ReservationResponse> getMyReservations(Long userId) {
        log.info("내 예약 목록 조회 - userId: {}", userId);

        List<Reservation> reservations = reservationRepository
                .findByUserIdOrderByReservedAtDesc(userId);

        log.info("조회된 예약 수: {}", reservations.size());

        return reservations.stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 강좌별 예약 목록 조회
     * 강좌 ID로 모든 예약을 조회하고 최근 예약 순으로 정렬합니다.
     */
    public List<ReservationResponse> getCourseReservations(Long courseId) {
        log.info("강좌별 예약 목록 조회 - courseId: {}", courseId);

        List<Reservation> reservations = reservationRepository
                .findByCourseIdOrderByReservedAtDesc(courseId);

        log.info("조회된 예약 수: {}", reservations.size());

        return reservations.stream()
                .map(ReservationResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 예약 취소
     * 예약을 취소하고 강좌 정원을 감소시킵니다. 취소 가능한 상태인지 확인합니다.
     */
    @Transactional
    public void cancelReservation(Long reservationId, String reason) {
        log.info("예약 취소 시작 - reservationId: {}, reason: {}", reservationId, reason);

        // 1. 예약 조회
        Reservation reservation = reservationRepository
                .findByIdWithCourse(reservationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "예약을 찾을 수 없습니다. ID: " + reservationId));

        // 2. 취소 가능 여부 확인
        if (!reservation.isCancellable()) {
            throw new IllegalStateException(
                    "취소할 수 없는 예약입니다. 현재 상태: " + reservation.getStatus());
        }

        // 3. 예약 취소
        reservation.cancel(reason);

        // 4. 강좌 정원 감소
        reservation.getCourse().decreaseCapacity();

        log.info("예약 취소 완료 - reservationId: {}", reservationId);
    }
}
