package com.aquarush.ticketing.course.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long centerId;

    @Column(nullable = false, length = 50)
    private String centerName;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(length = 30)
    private String subcategory;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 스케줄 정보 (JSON 형태로 저장 또는 별도 테이블)
    @Column(nullable = false, length = 100)
    private String weekdays; // 예: "월,수,금"

    @Column(nullable = false, length = 20)
    private String timeSlot; // 예: "14:00-15:00"

    @Column(nullable = false, length = 20)
    private String targetAudience; // 예: "성인"

    // 정원 정보
    @Column(nullable = false)
    private Integer currentCapacity; // 현재 신청 인원

    @Column(nullable = false)
    private Integer maxCapacity; // 최대 정원

    @Column(nullable = false)
    private Integer price;

    @Column(length = 50)
    private String instructor; // 강사명

    @Column(length = 100)
    private String location; // 장소

    @Column(nullable = false)
    private LocalDate startDate; // 강좌 시작일

    @Column(nullable = false)
    private LocalDate endDate; // 강좌 종료일

    @Column(nullable = false)
    private Integer totalSessions; // 총 회차

    @Column(length = 100)
    private String requirements; // 준비물

    @Column(length = 20)
    private String level; // 난이도

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CourseStatus status; // 강좌 상태

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = CourseStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 비즈니스 메서드
    public boolean isAvailable() {
        return this.status == CourseStatus.ACTIVE
                && this.currentCapacity < this.maxCapacity;
    }

    public int getAvailableSeats() {
        return this.maxCapacity - this.currentCapacity;
    }

    public void increaseCapacity() {
        if (this.currentCapacity >= this.maxCapacity) {
            throw new IllegalStateException("정원이 초과되었습니다.");
        }
        this.currentCapacity++;

        if (this.currentCapacity >= this.maxCapacity) {
            this.status = CourseStatus.FULL;
        }
    }

    public void decreaseCapacity() {
        if (this.currentCapacity <= 0) {
            throw new IllegalStateException("현재 신청 인원이 0입니다.");
        }
        this.currentCapacity--;

        if (this.status == CourseStatus.FULL) {
            this.status = CourseStatus.ACTIVE;
        }
    }
}
