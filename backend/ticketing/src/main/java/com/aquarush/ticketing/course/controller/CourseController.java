package com.aquarush.ticketing.course.controller;

import com.aquarush.ticketing.course.dto.CourseDetailResponse;
import com.aquarush.ticketing.course.dto.CourseSearchRequest;
import com.aquarush.ticketing.course.dto.CourseSearchResponse;
import com.aquarush.ticketing.course.service.CourseService;
import com.aquarush.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Course", description = "강좌 API")
public class CourseController {

    private final CourseService courseService;

    /**
     * 강좌 상세 조회
     */
    @Operation(
            summary = "강좌 상세 조회",
            description = "강좌 ID로 강좌 상세 정보를 조회합니다."
    )
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourseDetail(
            @PathVariable Long courseId
    ) {
        log.info("GET /api/v1/courses/{} - 강좌 상세 조회", courseId);

        CourseDetailResponse course = courseService.getCourseDetail(courseId);

        return ResponseEntity.ok(ApiResponse.success(course));
    }

    /**
     * 강좌 검색 (기본)
     */
    @Operation(
            summary = "강좌 검색",
            description = "다양한 조건으로 강좌를 검색합니다. 목록 조회에 최적화된 응답을 반환합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, List<CourseSearchResponse>>>> searchCourses(
            @RequestParam(required = false) Long centerId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String instructor
    ) {
        log.info("GET /api/v1/courses/search - centerId={}, categoryId={}, courseName={}, instructor={}",
                centerId, categoryId, courseName, instructor);

        CourseSearchRequest request = CourseSearchRequest.builder()
                .centerId(centerId)
                .categoryId(categoryId)
                .courseName(courseName)
                .instructor(instructor)
                .build();

        // CourseSearchResponse 리스트 반환 (목록용)
        List<CourseSearchResponse> courses = courseService.searchCourses(request);

        log.info("검색 결과: {}개", courses.size());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("courses", courses))
        );
    }
}