package com.aquarush.ticketing.course.controller;

import com.aquarush.ticketing.global.dto.ApiResponse;
import com.aquarush.ticketing.course.dto.CourseDetailResponse;
import com.aquarush.ticketing.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Courses", description = "강좌 API")
public class CourseController {

    private final CourseService courseService;

    /**
     * 강좌 상세 조회
     * GET /api/v1/courses/{courseId}
     */
    @Operation(
            summary = "센터 목록 조회",
            description = "센터 전체 목록을 조회합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "센터 목록 조회 성공")
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Map<String, CourseDetailResponse>>> getCourse(
            @PathVariable Long courseId
    ) {
        log.info("GET /courses/{} - 강좌 상세 조회 요청", courseId);

        CourseDetailResponse course = courseService.findCourseById(courseId);

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("course", course))
        );
    }
}
