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

    public CourseDetailResponse getCourseDetail(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("강좌를 찾을 수 없습니다. ID: " + courseId));
        return CourseDetailResponse.from(course);
    }

    public CourseDetailResponse getRandomCourse() {
        Course course = courseRepository.findRandomActiveCourse()
                .orElseThrow(() -> new IllegalArgumentException("활성화된 강좌가 없습니다."));
        return CourseDetailResponse.from(course);
    }

    public List<CourseSearchResponse> searchCourses(CourseSearchRequest request) {
        List<Course> courses = courseRepository.searchCourses(
                request.getCenterId(),
                request.getCategoryId(),
                request.getLevel(),
                request.getTargetAudience(),
                request.getCourseName(),
                request.getInstructor()
        );

        log.info("강좌 검색 결과: {}개", courses.size());

        return courses.stream()
                .map(CourseSearchResponse::from)
                .collect(Collectors.toList());
    }
}
