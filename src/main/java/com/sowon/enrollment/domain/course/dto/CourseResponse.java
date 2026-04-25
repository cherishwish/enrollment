package com.sowon.enrollment.domain.course.dto;

import com.sowon.enrollment.domain.course.Course;
import com.sowon.enrollment.domain.course.CourseStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CourseResponse(
        String id,
        String creatorId,
        String title,
        String description,
        Integer price,
        Integer maxCapacity,
        Integer currentCount,
        LocalDate startDate,
        LocalDate endDate,
        CourseStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCreatorId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getMaxCapacity(),
                course.getCurrentCount(),
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
