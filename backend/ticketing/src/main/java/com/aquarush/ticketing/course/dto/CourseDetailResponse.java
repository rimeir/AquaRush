package com.aquarush.ticketing.course.dto;

import com.aquarush.ticketing.course.entity.Course;
import com.aquarush.ticketing.course.entity.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

    @Schema(description = "센터명", example = "센터 A")
    private String centerName;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "카테고리명", example = "초급")
    private String category;

    @Schema(description = "카테고리 설명")
    private String categoryDescription;

    @Schema(description = "하위 카테고리")
    private String subcategory;

    @Schema(description = "강좌명", example = "초급 수영 강좌")
    private String name;

    @Schema(description = "설명", example = "기초부터 배우는 수영 강좌")
    private String description;

    @Schema(description = "스케줄 정보")
    private ScheduleInfo schedule;

    @Schema(description = "대상", example = "성인")
    private String targetAudience;

    @Schema(description = "정원 정보")
    private CapacityInfo capacity;

    @Schema(description = "가격", example = "150000")
    private Integer price;

    @Schema(description = "강사명", example = "김코치")
    private String instructor;

    @Schema(description = "장소", example = "수영장 A")
    private String location;

    @Schema(description = "시작일", example = "2025-11-01")
    private LocalDate startDate;

    @Schema(description = "종료일", example = "2025-11-30")
    private LocalDate endDate;

    @Schema(description = "총 세션 수", example = "12")
    private Integer totalSessions;

    @Schema(description = "준비물/요구사항")
    private String requirements;

    @Schema(description = "난이도", example = "초급")
    private String level;

    @Schema(description = "강좌 상태", example = "ACTIVE")
    private CourseStatus status;

    /**
     * 중첩 클래스: 스케줄 정보
     */
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

    /**
     * 중첩 클래스: 정원 정보
     */
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

    /**
     * Entity → DTO 변환
     */
    public static CourseDetailResponse from(Course course) {
        // 정원 정보 계산
        int currentCapacity = course.getCurrentCapacity();
        int maxCapacity = course.getMaxCapacity();
        int availableSeats = course.getAvailableSeats();  // Course에 메서드 있음!
        boolean isAvailable = course.isAvailable();       // Course에 메서드 있음!

        // 스케줄 표시 (weekdays와 timeSlot 그대로 사용)
        String displayTime = course.getWeekdays() + " " + course.getTimeSlot();

        return CourseDetailResponse.builder()
                .id(course.getId())
                // Center 정보 (Course Entity에 직접 필드로 있음)
                .centerId(course.getCenterId())
                .centerName(course.getCenterName())
                // Category 정보 (연관관계)
                .categoryId(course.getCategory() != null ? course.getCategory().getId() : null)
                .category(course.getCategory() != null ? course.getCategory().getName() : null)
                .categoryDescription(course.getCategory() != null ? course.getCategory().getDescription() : null)
                // 하위 카테고리
                .subcategory(course.getSubcategory())
                // 강좌 기본 정보
                .name(course.getName())
                .description(course.getDescription())
                // 스케줄 정보 (Course Entity 필드 그대로 사용)
                .schedule(ScheduleInfo.builder()
                        .weekdays(course.getWeekdays())
                        .timeSlot(course.getTimeSlot())
                        .displayTime(displayTime)
                        .build())
                // 대상
                .targetAudience(course.getTargetAudience())
                // 정원 정보
                .capacity(CapacityInfo.builder()
                        .current(currentCapacity)
                        .max(maxCapacity)
                        .available(availableSeats)
                        .isAvailable(isAvailable)
                        .build())
                // 가격 (Integer 타입)
                .price(course.getPrice())
                // 강사명
                .instructor(course.getInstructor())
                // 장소
                .location(course.getLocation())
                // 날짜 정보
                .startDate(course.getStartDate())
                .endDate(course.getEndDate())
                // 총 세션 수
                .totalSessions(course.getTotalSessions())
                // 준비물
                .requirements(course.getRequirements())
                // 난이도
                .level(course.getLevel())
                // 상태
                .status(course.getStatus())
                .build();
    }
}