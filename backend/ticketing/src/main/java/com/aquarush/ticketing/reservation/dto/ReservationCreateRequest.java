package com.aquarush.ticketing.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "예약 생성 요청")
public class ReservationCreateRequest {

    @NotNull(message = "강좌 ID는 필수입니다.")
    @Schema(description = "강좌 ID", example = "1")
    private Long courseId;

    @NotNull(message = "사용자 ID는 필수입니다.")
    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @NotBlank(message = "이름은 필수입니다.")
    @Schema(description = "예약자 이름", example = "홍길동")
    private String userName;

    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^01[0-9]-[0-9]{4}-[0-9]{4}$",
            message = "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)")
    @Schema(description = "전화번호", example = "010-1234-5678")
    private String userPhone;

    @Schema(description = "이메일", example = "hong@example.com")
    private String userEmail;

    @Schema(description = "세션 ID (대기열 식별용)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;
}
