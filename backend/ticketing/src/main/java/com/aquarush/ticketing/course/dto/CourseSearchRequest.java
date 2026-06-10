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

    @Schema(description = "카테고리 ID (대분류)", example = "1")
    private Long categoryId;

    @Schema(description = "소분류 (난이도)", example = "초급", allowableValues = {"초급", "중급", "고급"})
    private String level;

    @Schema(description = "교육 대상", example = "성인", allowableValues = {"성인", "청소년", "어린이"})
    private String targetAudience;

    @Schema(description = "강좌명 (부분 검색)", example = "수영")
    private String courseName;

    @Schema(description = "강사명", example = "김코치")
    private String instructor;
}
