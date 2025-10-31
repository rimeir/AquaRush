package com.aquarush.ticketing.course.dto;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.entity.CourseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailResponse {

    private Long id;
    private Long centerId;
    private String centerName;
    private String category;
    private String subcategory;
    private String name;
    private String description;

    private ScheduleInfo schedule;
    private String targetAudience;

    private CapacityInfo capacity;

    private Integer price;
    private String instructor;
    private String location;

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalSessions;

    private String requirements;
    private String level;
    private CourseStatus status;

    // 중첩 클래스
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleInfo {
        private String weekdays;
        private String timeSlot;
        private String displayTime;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapacityInfo {
        private Integer current;
        private Integer max;
        private Integer available;
        private Boolean isAvailable;
    }

    // Entity → DTO 변환
    public static CourseDetailResponse from(Course course) {
        return CourseDetailResponse.builder()
                .id(course.getId())
                .centerId(course.getCenterId())
                .centerName(course.getCenterName())
                .category(course.getCategory())
                .subcategory(course.getSubcategory())
                .name(course.getName())
                .description(course.getDescription())
                .schedule(ScheduleInfo.builder()
                        .weekdays(course.getWeekdays())
                        .timeSlot(course.getTimeSlot())
                        .displayTime(course.getWeekdays() + " " + course.getTimeSlot())
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
                .location(course.getLocation())
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                .totalSessions(course.getTotalSessions())
                .requirements(course.getRequirements())
                .level(course.getLevel())
                .status(course.getStatus())
                .build();
    }
}
