package com.sowon.enrollment.domain.enrollment;

import com.sowon.enrollment.common.exception.CancellationPeriodExpiredException;
import com.sowon.enrollment.common.exception.InvalidStatusTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "user_id", "status"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "class_id", nullable = false, length = 36)
    private String classId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status;

    private LocalDateTime confirmedAt;

    private LocalDateTime cancelledAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Enrollment create(String id, String classId, String userId) {
        Enrollment enrollment = new Enrollment();
        enrollment.id = id;
        enrollment.classId = classId;
        enrollment.userId = userId;
        enrollment.status = EnrollmentStatus.PENDING;
        return enrollment;
    }

    public void confirm() {
        if (this.status != EnrollmentStatus.PENDING) {
            throw new InvalidStatusTransitionException(this.status.name(), "CONFIRMED");
        }
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == EnrollmentStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("CANCELLED", "CANCELLED");
        }
        if (this.status == EnrollmentStatus.CONFIRMED) {
            if (LocalDateTime.now().isAfter(this.confirmedAt.plusDays(7))) {
                throw new CancellationPeriodExpiredException(this.id);
            }
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
}
