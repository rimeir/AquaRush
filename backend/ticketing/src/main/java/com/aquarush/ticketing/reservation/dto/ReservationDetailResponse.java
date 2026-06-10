package com.aquarush.ticketing.reservation.dto;

import com.aquarush.ticketing.reservation.entity.Reservation;
import com.aquarush.ticketing.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "예약 상세 응답")
public class ReservationDetailResponse {

    @Schema(description = "예약 ID")
    private Long id;

    // 강좌 정보
    @Schema(description = "강좌 ID")
    private Long courseId;

    @Schema(description = "강좌명")
    private String courseName;

    @Schema(description = "센터명")
    private String centerName;

    @Schema(description = "카테고리명")
    private String categoryName;

    @Schema(description = "강사명")
    private String instructor;

    @Schema(description = "요일")
    private String weekdays;

    @Schema(description = "시간")
    private String timeSlot;

    @Schema(description = "가격")
    private Integer price;

    // 예약자 정보
    @Schema(description = "사용자 ID")
    private Long userId;

    @Schema(description = "예약자 이름")
    private String userName;

    @Schema(description = "전화번호")
    private String userPhone;

    @Schema(description = "이메일")
    private String userEmail;

    // 예약 상태
    @Schema(description = "예약 상태")
    private ReservationStatus status;

    @Schema(description = "예약 일시")
    private LocalDateTime reservedAt;

    @Schema(description = "취소 일시")
    private LocalDateTime cancelledAt;

    @Schema(description = "취소 사유")
    private String cancellationReason;

    @Schema(description = "취소 가능 여부")
    private boolean isCancellable;

    public static ReservationDetailResponse from(Reservation reservation) {
        return ReservationDetailResponse.builder()
                .id(reservation.getId())
                .courseId(reservation.getCourse().getId())
                .courseName(reservation.getCourse().getName())
                .centerName(reservation.getCourse().getCenterName())
                .categoryName(reservation.getCourse().getCategory().getName())
                .instructor(reservation.getCourse().getInstructor())
                .weekdays(reservation.getCourse().getWeekdays())
                .timeSlot(reservation.getCourse().getTimeSlot())
                .price(reservation.getCourse().getPrice())
                .userId(reservation.getUserId())
                .userName(reservation.getUserName())
                .userPhone(reservation.getUserPhone())
                .userEmail(reservation.getUserEmail())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .cancelledAt(reservation.getCancelledAt())
                .cancellationReason(reservation.getCancellationReason())
                .isCancellable(reservation.isCancellable())
                .build();
    }
}
