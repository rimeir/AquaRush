package com.aquarush.ticketing.reservation.repository;

import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 예약 리포지토리
 *
 * ReservationService에서 사용하는 모든 메서드 포함
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 활성 예약 존재 여부 확인
     *
     * 사용처: 중복 예약 방지
     *
     * @param courseId 강좌 ID
     * @param userId 사용자 ID
     * @return 활성 예약 존재 여부
     *
     * 생성되는 SQL:
     * SELECT COUNT(*) > 0
     * FROM reservations
     * WHERE course_id = ?
     *   AND user_id = ?
     *   AND status IN ('CONFIRMED', 'PENDING')
     */
    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
            "WHERE r.course.id = :courseId " +
            "AND r.userId = :userId " +
            "AND r.status IN ('CONFIRMED', 'PENDING')")
    boolean existsActiveByCourseIdAndUserId(
            @Param("courseId") Long courseId,
            @Param("userId") Long userId
    );

    /**
     * ID로 예약 조회 (Course 정보 포함)
     *
     * @param id 예약 ID
     * @return 예약 (Course와 함께)
     *
     * JPQL fetch join 사용:
     * - N+1 문제 방지
     * - 한 번의 쿼리로 Reservation과 Course 모두 조회
     */
    @Query("SELECT r FROM Reservation r " +
            "JOIN FETCH r.course " +
            "WHERE r.id = :id")
    Optional<Reservation> findByIdWithCourse(@Param("id") Long id);

    /**
     * 사용자 ID로 예약 목록 조회 (Course 정보 포함)
     *
     * @param userId 사용자 ID
     * @return 예약 목록
     */
    @Query("SELECT r FROM Reservation r " +
            "JOIN FETCH r.course " +
            "WHERE r.userId = :userId " +
            "ORDER BY r.createdAt DESC")
    List<Reservation> findByUserIdWithCourse(@Param("userId") Long userId);

    /**
     * 강좌 ID로 예약 목록 조회
     *
     * @param courseId 강좌 ID
     * @return 예약 목록
     */
    @Query("SELECT r FROM Reservation r " +
            "WHERE r.course.id = :courseId " +
            "ORDER BY r.createdAt DESC")
    List<Reservation> findByCourseId(@Param("courseId") Long courseId);

    /**
     * 강좌별 활성 예약 수 조회
     *
     * @param courseId 강좌 ID
     * @return 활성 예약 수
     */
    @Query("SELECT COUNT(r) FROM Reservation r " +
            "WHERE r.course.id = :courseId " +
            "AND r.status IN ('CONFIRMED', 'PENDING')")
    long countActiveByCourseId(@Param("courseId") Long courseId);

    /**
     * 상태별 예약 조회
     *
     * @param status 예약 상태
     * @return 예약 목록
     */
    List<Reservation> findByStatus(ReservationStatus status);

    /**
     * 특정 유저보다 먼저 생성된 활성 예약 수 조회 (순위 계산용)
     *
     * @param courseId 강좌 ID
     * @param userId 유저 ID
     * @return 해당 유저보다 먼저 생성된 예약 수 (0이면 1등)
     */
    @Query("SELECT COUNT(r) FROM Reservation r " +
            "WHERE r.course.id = :courseId " +
            "AND r.status IN ('CONFIRMED', 'PENDING') " +
            "AND r.createdAt < (" +
            "  SELECT r2.createdAt FROM Reservation r2 " +
            "  WHERE r2.course.id = :courseId " +
            "  AND r2.userId = :userId " +
            "  AND r2.status IN ('CONFIRMED', 'PENDING')" +
            ")")
    long countReservationsBeforeUser(@Param("courseId") Long courseId, @Param("userId") Long userId);
}