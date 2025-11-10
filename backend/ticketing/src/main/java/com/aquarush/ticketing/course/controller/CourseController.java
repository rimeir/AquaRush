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
     * 강좌 검색
     */
    @Operation(
            summary = "강좌 검색",
            description = """
                    다양한 조건으로 강좌를 검색합니다.
                    
                    - centerId: 센터 ID로 필터링
                    - categoryId: 카테고리 ID로 필터링
                    - courseName: 강좌명으로 검색 (부분 일치)
                    - instructor: 강사명으로 검색 (부분 일치)
                    - weekday: 요일로 필터링 (예: "월", "월,수")
                    
                    모든 파라미터는 선택사항이며, 조합하여 사용 가능합니다.
                    """
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, List<CourseSearchResponse>>>> searchCourses(
            @Parameter(description = "센터 ID", example = "1")
            @RequestParam(required = false) Long centerId,

            @Parameter(description = "카테고리 ID", example = "1")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "강좌명 (부분 검색)", example = "초급")
            @RequestParam(required = false) String courseName,

            @Parameter(description = "강사명 (부분 검색)", example = "김코치")
            @RequestParam(required = false) String instructor,

            @Parameter(
                    description = "요일 필터 (예: '월', '월,수')",
                    example = "월"
            )
            @RequestParam(required = false) String weekday
    ) {
        log.info("GET /api/v1/courses/search - centerId={}, categoryId={}, courseName={}, instructor={}, weekday={}",
                centerId, categoryId, courseName, instructor, weekday);

        CourseSearchRequest request = CourseSearchRequest.builder()
                .centerId(centerId)
                .categoryId(categoryId)
                .courseName(courseName)
                .instructor(instructor)
                .weekday(weekday)
                .build();

        List<CourseSearchResponse> courses = courseService.searchCourses(request);

        log.info("검색 결과: {}개", courses.size());

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("courses", courses))
        );
    }
}