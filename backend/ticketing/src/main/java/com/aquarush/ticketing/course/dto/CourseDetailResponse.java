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
@Schema(description = "강좌 상세 응답")
public class CourseDetailResponse {

    @Schema(description = "강좌 ID", example = "1")
    private Long id;

    @Schema(description = "센터 ID", example = "1")
    private Long centerId;

    @Schema(description = "센터명", example = "고등어 스포츠센터")
    private String centerName;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "카테고리명 (대분류)", example = "수영")
    private String category;

    @Schema(description = "강좌명", example = "성인 자유수영 초급반")
    private String name;

    @Schema(description = "설명")
    private String description;

    @Schema(description = "스케줄 정보")
    private ScheduleInfo schedule;

    @Schema(description = "교육 대상", example = "성인")
    private String targetAudience;

    @Schema(description = "정원 정보")
    private CapacityInfo capacity;

    @Schema(description = "가격", example = "80000")
    private Integer price;

    @Schema(description = "강사명", example = "김코치")
    private String instructor;

    @Schema(description = "소분류 (난이도)", example = "초급")
    private String level;

    @Schema(description = "강좌 상태", example = "ACTIVE")
    private CourseStatus status;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "스케줄 정보")
    public static class ScheduleInfo {
        @Schema(description = "요일", example = "월,수,금")
        private String weekdays;

        @Schema(description = "시간대", example = "14:00-15:00")
        private String timeSlot;

        @Schema(description = "전체 표시", example = "월,수,금 14:00-15:00")
        private String displayTime;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "정원 정보")
    public static class CapacityInfo {
        @Schema(description = "현재 신청 인원", example = "15")
        private Integer current;

        @Schema(description = "최대 정원", example = "20")
        private Integer max;

        @Schema(description = "남은 자리", example = "5")
        private Integer available;

        @Schema(description = "신청 가능 여부", example = "true")
        private Boolean isAvailable;
    }

    public static CourseDetailResponse from(Course course) {
        String displayTime = course.getWeekdays() + " " + course.getTimeSlot();

        return CourseDetailResponse.builder()
                .id(course.getId())
                .centerId(course.getCenterId())
                .centerName(course.getCenterName())
                .categoryId(course.getCategory() != null ? course.getCategory().getId() : null)
                .category(course.getCategory() != null ? course.getCategory().getName() : null)
                .name(course.getName())
                .description(course.getDescription())
                .schedule(ScheduleInfo.builder()
                        .weekdays(course.getWeekdays())
                        .timeSlot(course.getTimeSlot())
                        .displayTime(displayTime)
                        .build())
                .targetAudience(course.getTargetAudience())
                .capacity(CapacityInfo.builder()
                        .current(course.getCurrentCapacity())
                        .max(course.getMaxCapacity())
                        .available(course.getAvailableSeats())
                        .isAvailable(course.isAvailable())
                        .build())
                .price(course.getPrice())
                .instructor(course.getInstructor())
                .level(course.getLevel())
                .status(course.getStatus())
                .build();
    }
}
