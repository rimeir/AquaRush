package com.aquarush.ticketing.course.dto;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.entity.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강좌 검색 결과")
public class CourseSearchResponse {

    @Schema(description = "강좌 ID", example = "1")
    private Long id;

    @Schema(description = "센터 ID", example = "1")
    private Long centerId;

    @Schema(description = "센터명", example = "고등어 스포츠센터")
    private String centerName;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "카테고리명 (대분류)", example = "수영")
    private String categoryName;

    @Schema(description = "강좌명", example = "성인 자유수영 초급반")
    private String courseName;

    @Schema(description = "강사명", example = "김코치")
    private String instructor;

    @Schema(description = "요일", example = "월,수,금")
    private String weekdays;

    @Schema(description = "시간대", example = "14:00-15:00")
    private String timeSlot;

    @Schema(description = "교육 대상", example = "성인")
    private String targetAudience;

    @Schema(description = "소분류 (난이도)", example = "초급")
    private String level;

    @Schema(description = "가격", example = "80000")
    private Integer price;

    @Schema(description = "현재 신청 인원", example = "0")
    private Integer currentCapacity;

    @Schema(description = "최대 정원", example = "20")
    private Integer maxCapacity;

    @Schema(description = "남은 자리", example = "20")
    private Integer availableSeats;

    @Schema(description = "신청 가능 여부", example = "true")
    private Boolean isAvailable;

    @Schema(description = "강좌 상태", example = "ACTIVE")
    private CourseStatus status;

    public static CourseSearchResponse from(Course course) {
        return CourseSearchResponse.builder()
                .id(course.getId())
                .centerId(course.getCenterId())
                .centerName(course.getCenterName())
                .categoryId(course.getCategory() != null ? course.getCategory().getId() : null)
                .categoryName(course.getCategory() != null ? course.getCategory().getName() : null)
                .courseName(course.getName())
                .instructor(course.getInstructor())
                .weekdays(course.getWeekdays())
                .timeSlot(course.getTimeSlot())
                .targetAudience(course.getTargetAudience())
                .level(course.getLevel())
                .price(course.getPrice())
                .currentCapacity(course.getCurrentCapacity())
                .maxCapacity(course.getMaxCapacity())
                .availableSeats(course.getAvailableSeats())
                .isAvailable(course.isAvailable())
                .status(course.getStatus())
                .build();
    }
}
