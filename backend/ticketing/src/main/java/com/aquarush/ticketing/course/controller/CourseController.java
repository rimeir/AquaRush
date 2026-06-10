package com.aquarush.ticketing.course.controller;

import com.aquarush.ticketing.course.dto.CourseDetailResponse;
import com.aquarush.ticketing.course.dto.CourseSearchRequest;
import com.aquarush.ticketing.course.dto.CourseSearchResponse;
import com.aquarush.ticketing.course.service.CourseService;
import com.aquarush.ticketing.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(summary = "강좌 상세 조회")
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourseDetail(
            @PathVariable Long courseId
    ) {
        return ResponseEntity.ok(ApiResponse.success(courseService.getCourseDetail(courseId)));
    }

    @Operation(summary = "랜덤 강좌 조회", description = "ACTIVE 상태의 강좌 중 랜덤으로 1개를 반환합니다. 시뮬레이션 미션 강좌 선택에 사용됩니다.")
    @GetMapping("/random")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getRandomCourse() {
        return ResponseEntity.ok(ApiResponse.success(courseService.getRandomCourse()));
    }

    @Operation(summary = "강좌 검색", description = "센터·대분류·소분류·교육 대상으로 강좌를 필터링합니다.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, List<CourseSearchResponse>>>> searchCourses(
            @Parameter(description = "센터 ID") @RequestParam(required = false) Long centerId,
            @Parameter(description = "카테고리 ID (대분류)") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "소분류 (초급/중급/고급)") @RequestParam(required = false) String level,
            @Parameter(description = "교육 대상 (성인/청소년/어린이)") @RequestParam(required = false) String targetAudience,
            @Parameter(description = "강좌명 (부분 검색)") @RequestParam(required = false) String courseName,
            @Parameter(description = "강사명 (부분 검색)") @RequestParam(required = false) String instructor
    ) {
        CourseSearchRequest request = CourseSearchRequest.builder()
                .centerId(centerId)
                .categoryId(categoryId)
                .level(level)
                .targetAudience(targetAudience)
                .courseName(courseName)
                .instructor(instructor)
                .build();

        List<CourseSearchResponse> courses = courseService.searchCourses(request);
        return ResponseEntity.ok(ApiResponse.success(Map.of("courses", courses)));
    }
}
