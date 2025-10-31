package com.aquarush.ticketing.course.service;

import com.aquarush.ticketing.global.exception.NotFoundException;
import com.aquarush.ticketing.course.dto.CourseDetailResponse;
import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;

    /**
     * 강좌 상세 조회
     */
    public CourseDetailResponse findCourseById(Long courseId) {
        log.info("강좌 상세 조회 - courseId: {}", courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("강좌를 찾을 수 없습니다. ID: " + courseId));

        log.info("강좌 조회 완료 - {}", course.getName());

        return CourseDetailResponse.from(course);
    }
}
