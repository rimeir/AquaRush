package com.aquarush.ticketing.course.repository;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.entity.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    // 특정 센터의 활성 강좌 조회
    List<Course> findByCenterIdAndStatus(Long centerId, CourseStatus status);

    // ID로 조회 (존재 여부 확인용)
    boolean existsById(Long id);

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
