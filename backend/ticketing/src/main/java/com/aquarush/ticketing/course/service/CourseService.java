package com.aquarush.ticketing.course.service;

import com.aquarush.ticketing.course.dto.CourseDetailResponse;
import com.aquarush.ticketing.course.dto.CourseSearchRequest;
import com.aquarush.ticketing.course.dto.CourseSearchResponse;
import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.repository.CourseRepository;
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
public class CourseService {

    private final CourseRepository courseRepository;

    /**
     * 강좌 상세 조회
     */
    public CourseDetailResponse getCourseDetail(Long courseId) {
        log.info("강좌 상세 조회 - courseId: {}", courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("강좌를 찾을 수 없습니다. ID: " + courseId));

        return CourseDetailResponse.from(course);
    }

    /**
     * 강좌 검색 (기본)
     */
    public List<CourseSearchResponse> searchCourses(CourseSearchRequest request) {
        log.info("강좌 검색 - 조건: centerId={}, categoryId={}, courseName={}, instructor={}",
                request.getCenterId(),
                request.getCategoryId(),
                request.getCourseName(),
                request.getInstructor());

        List<Course> courses = courseRepository.searchCourses(
                request.getCenterId(),
                request.getCategoryId(),
                request.getCourseName(),
                request.getInstructor()
        );

        log.info("검색 결과: {}개", courses.size());

        // Entity → CourseSearchResponse 변환 (목록용)
        return courses.stream()
                .map(CourseSearchResponse::from)
                .collect(Collectors.toList());
    }
}