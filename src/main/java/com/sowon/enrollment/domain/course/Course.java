package com.sowon.enrollment.domain.course;

import com.sowon.enrollment.common.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String creatorId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer maxCapacity;

    @Column(nullable = false)
    private Integer currentCount;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CourseStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static Course create(String id, String creatorId, String title, String description,
                                Integer price, Integer maxCapacity,
                                LocalDate startDate, LocalDate endDate) {
        Course course = new Course();
        course.id = id;
        course.creatorId = creatorId;
        course.title = title;
        course.description = description;
        course.price = price;
        course.maxCapacity = maxCapacity;
        course.currentCount = 0;
        course.startDate = startDate;
        course.endDate = endDate;
        course.status = CourseStatus.DRAFT;
        return course;
    }

    public void changeStatus(CourseStatus newStatus) {
        boolean valid = switch (this.status) {
            case DRAFT  -> newStatus == CourseStatus.OPEN;
            case OPEN   -> newStatus == CourseStatus.CLOSED;
            case CLOSED -> newStatus == CourseStatus.OPEN;
        };
        if (!valid) {
            throw new InvalidStatusTransitionException(this.status.name(), newStatus.name());
        }
        this.status = newStatus;
    }

    public boolean isOpen() {
        return this.status == CourseStatus.OPEN;
    }

    public boolean isFull() {
        return this.currentCount >= this.maxCapacity;
    }

    public void incrementCurrentCount() {
        this.currentCount++;
    }

    public void decrementCurrentCount() {
        if (this.currentCount > 0) {
            this.currentCount--;
        }
    }
}
