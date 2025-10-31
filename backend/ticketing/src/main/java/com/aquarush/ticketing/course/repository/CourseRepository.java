package com.aquarush.ticketing.course.repository;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.entity.CourseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    // 특정 센터의 활성 강좌 조회
    List<Course> findByCenterIdAndStatus(Long centerId, CourseStatus status);

    // ID로 조회 (존재 여부 확인용)
    boolean existsById(Long id);
}
