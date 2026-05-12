package com.aquarush.ticketing.course.repository;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.entity.CourseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 강좌 리포지토리
 *
 * ⭐ 개선 사항:
 * - findByIdWithLock() 메서드 추가 (비관적 락)
 * - 동시성 제어 강화
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    // 특정 센터의 활성 강좌 조회
    List<Course> findByCenterIdAndStatus(Long centerId, CourseStatus status);

    // ID로 조회 (존재 여부 확인용)
    boolean existsById(Long id);

    /**
     * ⭐ 비관적 락을 사용한 강좌 조회 (동시성 제어)
     *
     * SELECT ... FOR UPDATE를 사용하여
     * 다른 트랜잭션이 이 행을 수정하지 못하도록 잠금
     *
     * @param id 강좌 ID
     * @return 강좌 (잠금)
     *
     * @apiNote 예약 생성 시 정원 초과를 방지하기 위해 사용
     *
     * 사용 예시:
     * <pre>
     * {@code
     * Course course = courseRepository.findByIdWithLock(courseId)
     *         .orElseThrow(() -> new IllegalArgumentException("강좌 없음"));
     *
     * if (course.isAvailable()) {
     *     // 예약 생성
     *     course.increaseCapacity();
     *     // 다른 트랜잭션은 이 강좌를 수정할 수 없음
     * }
     * }
     * </pre>
     *
     * 생성되는 SQL:
     * <pre>
     * SELECT * FROM courses WHERE id = ? FOR UPDATE
     * </pre>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdWithLock(@Param("id") Long id);

    /**
     * 강좌 검색 (기본)
     * - c.center.id → c.centerId (Course에 center 연관관계 없음)
     * - c.category.id → c.category.id (Category는 연관관계 있음)
     */
    @Query("""
            SELECT c FROM Course c
            WHERE (:centerId IS NULL OR c.centerId = :centerId)
            AND (:categoryId IS NULL OR c.category.id = :categoryId)
            AND (:courseName IS NULL OR c.name LIKE CONCAT('%', :courseName, '%'))
            AND (:instructor IS NULL OR c.instructor LIKE CONCAT('%', :instructor, '%'))
            AND (:weekday IS NULL OR c.weekdays LIKE CONCAT('%', :weekday, '%'))
            ORDER BY c.startDate ASC, c.timeSlot ASC
            """)
    List<Course> searchCourses(
            @Param("centerId") Long centerId,
            @Param("categoryId") Long categoryId,
            @Param("courseName") String courseName,
            @Param("instructor") String instructor,
            @Param("weekday") String weekday
    );

    /**
     * 센터별 강좌 목록 조회
     */
    List<Course> findByCenterId(Long centerId);

    /**
     * 카테고리별 강좌 목록 조회
     */
    List<Course> findByCategoryId(Long categoryId);

    /**
     * 센터 + 카테고리 조합 조회
     */
    List<Course> findByCenterIdAndCategoryId(Long centerId, Long categoryId);

    /**
     * 강좌명으로 검색 (부분 일치)
     */
    List<Course> findByNameContaining(String name);
}