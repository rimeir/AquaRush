package com.aquarush.ticketing.course.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "강좌 검색 조건")
public class CourseSearchRequest {

    @Schema(description = "센터 ID", example = "1")
    private Long centerId;

    @Schema(description = "카테고리 ID", example = "1")
    private Long categoryId;

    @Schema(description = "강좌명 (부분 검색)", example = "수영")
    private String courseName;

    @Schema(description = "강사명", example = "김코치")
    private String instructor;

    @Schema(
            description = "요일 (복수 선택 가능, 쉼표로 구분)",
            example = "월,수",
            allowableValues = {"월", "화", "수", "목", "금", "토", "일"}
    )
    private String weekday;
}