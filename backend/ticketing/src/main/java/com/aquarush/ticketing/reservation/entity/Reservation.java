package com.aquarush.ticketing.reservation.entity;

import com.aquarush.ticketing.course.entity.Course;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 강좌 연관관계
     * 하나의 예약은 하나의 강좌에 속함
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * 사용자 정보
     * 추후 User Entity와 연관관계 설정 예정
     */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String userName;

    @Column(nullable = false, length = 20)
    private String userPhone;

    @Column(length = 100)
    private String userEmail;

    /**
     * 예약 상태
     */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    /**
     * 예약 일시
     */
    @Column(nullable = false)
    private LocalDateTime reservedAt;

    /**
     * 취소 정보
     */
    private LocalDateTime cancelledAt;

    @Column(length = 200)
    private String cancellationReason;

    /**
     * 생성/수정 일시
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.reservedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ReservationStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 비즈니스 메서드
     */

    // 예약 확정
    public void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 예약만 확정할 수 있습니다.");
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    // 예약 취소
    public void cancel(String reason) {
        if (this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }
        if (this.status == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("완료된 예약은 취소할 수 없습니다.");
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancellationReason = reason;
    }

    // 예약 완료 처리
    public void complete() {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 예약만 완료 처리할 수 있습니다.");
        }
        this.status = ReservationStatus.COMPLETED;
    }

    // 취소 가능 여부
    public boolean isCancellable() {
        return this.status == ReservationStatus.PENDING
                || this.status == ReservationStatus.CONFIRMED;
    }
}
