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

import java.util.ArrayList;
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
     * 강좌 검색
     *
     * 방식 1: Service에서 여러 요일 처리 (프론트가 "월,수" 형태로 보내면)
     */
    public List<CourseSearchResponse> searchCourses(CourseSearchRequest request) {
        log.info("강좌 검색 - 조건: centerId={}, categoryId={}, courseName={}, instructor={}, weekday={}",
                request.getCenterId(),
                request.getCategoryId(),
                request.getCourseName(),
                request.getInstructor(),
                request.getWeekday());

        String weekday = request.getWeekday();

        // 요일이 없거나 단일 요일이면 그대로 검색
        if (weekday == null || !weekday.contains(",")) {
            return searchByWeekday(request, weekday);
        }

        // 여러 요일이면 각각 검색 후 합치기
        return searchMultipleWeekdays(request, weekday);
    }

    /**
     * 단일 요일 검색
     */
    private List<CourseSearchResponse> searchByWeekday(CourseSearchRequest request, String weekday) {
        List<Course> courses = courseRepository.searchCourses(
                request.getCenterId(),
                request.getCategoryId(),
                request.getCourseName(),
                request.getInstructor(),
                weekday
        );

        log.info("검색 결과: {}개", courses.size());

        return courses.stream()
                .map(CourseSearchResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 여러 요일 OR 검색
     * "월,수,금" → 월 OR 수 OR 금
     */
    private List<CourseSearchResponse> searchMultipleWeekdays(CourseSearchRequest request, String weekdays) {
        String[] days = weekdays.split(",");
        log.info("여러 요일 검색: {}", String.join(", ", days));

        List<CourseSearchResponse> allResults = new ArrayList<>();

        // 각 요일별로 검색
        for (String day : days) {
            String trimmedDay = day.trim();
            if (!trimmedDay.isEmpty()) {
                List<Course> courses = courseRepository.searchCourses(
                        request.getCenterId(),
                        request.getCategoryId(),
                        request.getCourseName(),
                        request.getInstructor(),
                        trimmedDay
                );

                courses.stream()
                        .map(CourseSearchResponse::from)
                        .forEach(allResults::add);
            }
        }

        // 중복 제거 (같은 ID의 강좌가 여러 번 나올 수 있음)
        List<CourseSearchResponse> uniqueResults = allResults.stream()
                .collect(Collectors.toMap(
                        CourseSearchResponse::getId,
                        course -> course,
                        (existing, replacement) -> existing
                ))
                .values()
                .stream()
                .collect(Collectors.toList());

        log.info("검색 결과: {}개 (중복 제거 후)", uniqueResults.size());

        return uniqueResults;
    }
}