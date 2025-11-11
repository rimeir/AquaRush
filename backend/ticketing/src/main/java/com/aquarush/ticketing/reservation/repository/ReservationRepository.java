package com.aquarush.ticketing.reservation.repository;

import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 사용자별 예약 목록 조회
     * 최근 예약 순으로 정렬
     */
    List<Reservation> findByUserIdOrderByReservedAtDesc(Long userId);

    /**
     * 강좌별 예약 목록 조회
     * 최근 예약 순으로 정렬
     */
    List<Reservation> findByCourseIdOrderByReservedAtDesc(Long courseId);

    /**
     * 사용자의 특정 강좌 예약 여부 확인
     * 활성 상태(PENDING, CONFIRMED)의 예약만 확인
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM Reservation r
            WHERE r.userId = :userId
            AND r.course.id = :courseId
            AND r.status IN ('PENDING', 'CONFIRMED')
            """)
    boolean existsByUserIdAndCourseIdAndActiveStatus(
            @Param("userId") Long userId,
            @Param("courseId") Long courseId
    );

    /**
     * 상태별 예약 조회
     * 최근 예약 순으로 정렬
     */
    List<Reservation> findByStatusOrderByReservedAtDesc(ReservationStatus status);

    /**
     * 예약 상세 조회 (Course fetch join)
     * N+1 문제 방지를 위해 Course와 Category를 함께 조회
     */
    @Query("""
            SELECT r
            FROM Reservation r
            JOIN FETCH r.course c
            LEFT JOIN FETCH c.category
            WHERE r.id = :id
            """)
    Optional<Reservation> findByIdWithCourse(@Param("id") Long id);
}
