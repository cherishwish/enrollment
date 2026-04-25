package com.sowon.enrollment.domain.enrollment.dto;

import com.sowon.enrollment.domain.enrollment.Enrollment;
import com.sowon.enrollment.domain.enrollment.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentResponse(
        String id,
        String classId,
        String userId,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getClassId(),
                enrollment.getUserId(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt(),
                enrollment.getCreatedAt()
        );
    }
}
