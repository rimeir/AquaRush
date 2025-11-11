package com.aquarush.ticketing.reservation.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReservationStatus {
    PENDING("예약 대기", "예약이 접수되었습니다."),
    CONFIRMED("예약 확정", "예약이 확정되었습니다."),
    CANCELLED("예약 취소", "예약이 취소되었습니다."),
    COMPLETED("수강 완료", "수강이 완료되었습니다.");

    private final String displayName;
    private final String description;
}
