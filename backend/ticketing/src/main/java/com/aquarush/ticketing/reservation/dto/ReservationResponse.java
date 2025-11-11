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
@Schema(description = "예약 응답")
public class ReservationResponse {

    @Schema(description = "예약 ID")
    private Long id;

    @Schema(description = "강좌 ID")
    private Long courseId;

    @Schema(description = "강좌명")
    private String courseName;

    @Schema(description = "사용자 ID")
    private Long userId;

    @Schema(description = "예약자 이름")
    private String userName;

    @Schema(description = "예약 상태")
    private ReservationStatus status;

    @Schema(description = "예약 일시")
    private LocalDateTime reservedAt;

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .courseId(reservation.getCourse().getId())
                .courseName(reservation.getCourse().getName())
                .userId(reservation.getUserId())
                .userName(reservation.getUserName())
                .status(reservation.getStatus())
                .reservedAt(reservation.getReservedAt())
                .build();
    }
}
