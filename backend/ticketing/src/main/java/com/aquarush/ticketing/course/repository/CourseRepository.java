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

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByCenterIdAndStatus(Long centerId, CourseStatus status);

    boolean existsById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdWithLock(@Param("id") Long id);

    @Query("""
            SELECT c FROM Course c
            WHERE (:centerId IS NULL OR c.centerId = :centerId)
            AND (:categoryId IS NULL OR c.category.id = :categoryId)
            AND (:level IS NULL OR c.level = :level)
            AND (:targetAudience IS NULL OR c.targetAudience = :targetAudience)
            AND (:courseName IS NULL OR c.name LIKE CONCAT('%', :courseName, '%'))
            AND (:instructor IS NULL OR c.instructor LIKE CONCAT('%', :instructor, '%'))
            ORDER BY c.centerName ASC, c.timeSlot ASC
            """)
    List<Course> searchCourses(
            @Param("centerId") Long centerId,
            @Param("categoryId") Long categoryId,
            @Param("level") String level,
            @Param("targetAudience") String targetAudience,
            @Param("courseName") String courseName,
            @Param("instructor") String instructor
    );

    @Query(value = """
            SELECT c.* FROM courses c
            JOIN categories cat ON c.category_id = cat.id
            WHERE c.status = 'ACTIVE'
            AND cat.name = '수영'
            AND c.target_audience = '성인/청소년'
            ORDER BY RAND() LIMIT 1
            """, nativeQuery = true)
    Optional<Course> findRandomActiveCourse();

    List<Course> findByCenterId(Long centerId);

    List<Course> findByCategoryId(Long categoryId);

    List<Course> findByNameContaining(String name);
}
