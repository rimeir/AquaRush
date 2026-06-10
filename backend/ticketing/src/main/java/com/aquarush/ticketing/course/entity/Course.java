package com.aquarush.ticketing.course.entity;

import com.aquarush.ticketing.category.entity.Category;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(length = 30)
    private String subcategory;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String weekdays;

    @Column(nullable = false, length = 20)
    private String timeSlot;

    @Column(nullable = false, length = 20)
    private String targetAudience;

    @Column(nullable = false)
    private Integer currentCapacity;

    @Column(nullable = false)
    private Integer maxCapacity;

    @Column(nullable = false)
    private Integer price;

    @Column(length = 50)
    private String instructor;

    @Column(length = 20)
    private String level;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CourseStatus status;

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

    public boolean isAvailable() {
        if (this.status != CourseStatus.ACTIVE) {
            return false;
        }
        if (this.currentCapacity >= this.maxCapacity) {
            return false;
        }
        return true;
    }

    public int getAvailableSeats() {
        return Math.max(0, this.maxCapacity - this.currentCapacity);
    }

    public Integer getCurrentCapacity() {
        return this.currentCapacity;
    }

    public void increaseCapacity() {
        if (this.currentCapacity >= this.maxCapacity) {
            throw new IllegalStateException(
                    String.format("정원이 초과되었습니다: %d/%d", currentCapacity, maxCapacity));
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

    public void resetCapacity() {
        this.currentCapacity = 0;
        this.status = CourseStatus.ACTIVE;
    }

    public void setupForSimulation(int maxCapacity, int remainingSeats) {
        this.maxCapacity = maxCapacity;
        this.currentCapacity = Math.max(0, maxCapacity - remainingSeats);
        this.status = CourseStatus.ACTIVE;
    }

    public void changeStatus(CourseStatus newStatus) {
        this.status = newStatus;
    }

    public void activate() { changeStatus(CourseStatus.ACTIVE); }
    public void deactivate() { changeStatus(CourseStatus.CLOSED); }
    public void cancel() { changeStatus(CourseStatus.CANCELLED); }
    public void markAsFull() { changeStatus(CourseStatus.FULL); }

    public void updateInfo(String name, String description, Integer price) {
        if (name != null && !name.isBlank()) this.name = name;
        if (description != null) this.description = description;
        if (price != null && price >= 0) this.price = price;
    }
}
