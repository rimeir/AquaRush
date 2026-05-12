package com.aquarush.ticketing.course.entity;

import com.aquarush.ticketing.category.entity.Category;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 강좌 엔티티
 *
 * ⭐ 개선 사항:
 * - resetCapacity() 메서드 추가 (시뮬레이션 초기화)
 * - getCurrentCapacity() getter 명시
 * - isAvailable() 로직 개선
 * - 로깅 추가
 */
@Slf4j
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

    /**
     * 카테고리 연관관계
     * 하나의 강좌는 하나의 카테고리에 속함
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 30)
    private String subcategory;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // 스케줄 정보
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

    // ========================================
    // 비즈니스 메서드
    // ========================================

    /**
     * 예약 가능 여부 확인
     *
     * @return true: 예약 가능, false: 예약 불가
     */
    public boolean isAvailable() {
        // 1. 활성 상태 확인
        if (this.status != CourseStatus.ACTIVE) {
            log.debug("예약 불가 - 강좌 상태: courseId={}, status={}", id, status);
            return false;
        }

        // 2. 정원 확인
        if (this.currentCapacity >= this.maxCapacity) {
            log.debug("예약 불가 - 정원 초과: courseId={}, capacity={}/{}",
                    id, currentCapacity, maxCapacity);
            return false;
        }

        // 3. 날짜 확인 (선택적)
        LocalDate today = LocalDate.now();
        if (this.endDate != null && today.isAfter(this.endDate)) {
            log.debug("예약 불가 - 강좌 종료: courseId={}, endDate={}", id, endDate);
            return false;
        }

        return true;
    }

    /**
     * 남은 좌석 수 조회
     *
     * @return 남은 좌석 수
     */
    public int getAvailableSeats() {
        return Math.max(0, this.maxCapacity - this.currentCapacity);
    }

    /**
     * 현재 정원 조회 (명시적 getter)
     *
     * @return 현재 예약 인원
     */
    public Integer getCurrentCapacity() {
        return this.currentCapacity;
    }

    /**
     * 정원 증가 (예약 시)
     *
     * @throws IllegalStateException 정원 초과 시
     */
    public void increaseCapacity() {
        if (this.currentCapacity >= this.maxCapacity) {
            throw new IllegalStateException(
                    String.format("정원이 초과되었습니다: %d/%d",
                            currentCapacity, maxCapacity));
        }

        this.currentCapacity++;
        log.debug("정원 증가: courseId={}, capacity={}/{}",
                id, currentCapacity, maxCapacity);

        // 만석 시 상태 변경
        if (this.currentCapacity >= this.maxCapacity) {
            this.status = CourseStatus.FULL;
            log.info("강좌 만석: courseId={}, capacity={}/{}",
                    id, currentCapacity, maxCapacity);
        }
    }

    /**
     * 정원 감소 (예약 취소 시)
     *
     * @throws IllegalStateException 정원이 0 이하일 때
     */
    public void decreaseCapacity() {
        if (this.currentCapacity <= 0) {
            throw new IllegalStateException("현재 신청 인원이 0입니다.");
        }

        this.currentCapacity--;
        log.debug("정원 감소: courseId={}, capacity={}/{}",
                id, currentCapacity, maxCapacity);

        // 만석 상태였다면 활성으로 변경
        if (this.status == CourseStatus.FULL) {
            this.status = CourseStatus.ACTIVE;
            log.info("강좌 활성화: courseId={}, capacity={}/{}",
                    id, currentCapacity, maxCapacity);
        }
    }

    /**
     * ⭐ 정원 초기화 (시뮬레이션용)
     *
     * 시뮬레이션 시작 전에 호출하여 강좌를 초기 상태로 되돌립니다.
     * - 현재 정원을 0으로 설정
     * - 상태를 ACTIVE로 변경
     *
     * @apiNote 이 메서드는 테스트/시뮬레이션 목적으로만 사용해야 합니다.
     */
    public void resetCapacity() {
        log.info("🔄 정원 초기화: courseId={}, 이전 capacity={}/{}",
                id, currentCapacity, maxCapacity);

        this.currentCapacity = 0;
        this.status = CourseStatus.ACTIVE;

        log.info("✅ 정원 초기화 완료: courseId={}, capacity=0/{}, status={}",
                id, maxCapacity, status);
    }

    /**
     * 강좌 상태 변경
     *
     * @param newStatus 새로운 상태
     */
    public void changeStatus(CourseStatus newStatus) {
        log.info("강좌 상태 변경: courseId={}, {} → {}",
                id, this.status, newStatus);
        this.status = newStatus;
    }

    /**
     * 강좌 활성화
     */
    public void activate() {
        changeStatus(CourseStatus.ACTIVE);
    }

    /**
     * 강좌 비활성화
     */
    public void deactivate() {
        changeStatus(CourseStatus.CLOSED);
    }

    /**
     * 강좌 취소
     */
    public void cancel() {
        changeStatus(CourseStatus.CANCELLED);
    }

    /**
     * 강좌 만석 처리
     */
    public void markAsFull() {
        changeStatus(CourseStatus.FULL);
    }

    /**
     * 강좌 정보 업데이트 (선택적)
     *
     * @param name 강좌명
     * @param description 설명
     * @param price 가격
     */
    public void updateInfo(String name, String description, Integer price) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null && price >= 0) {
            this.price = price;
        }
        log.debug("강좌 정보 업데이트: courseId={}", id);
    }

    /**
     * 진행 중인 강좌인지 확인
     *
     * @return true: 진행 중, false: 아님
     */
    public boolean isOngoing() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(this.startDate) && !today.isAfter(this.endDate);
    }

    /**
     * 종료된 강좌인지 확인
     *
     * @return true: 종료됨, false: 아님
     */
    public boolean isEnded() {
        return LocalDate.now().isAfter(this.endDate);
    }

    /**
     * 시작 전 강좌인지 확인
     *
     * @return true: 시작 전, false: 아님
     */
    public boolean isUpcoming() {
        return LocalDate.now().isBefore(this.startDate);
    }
}