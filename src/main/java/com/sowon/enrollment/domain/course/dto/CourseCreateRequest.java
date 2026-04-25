package com.sowon.enrollment.domain.course.dto;

import java.time.LocalDate;

public record CourseCreateRequest(
        String title,
        String description,
        Integer price,
        Integer maxCapacity,
        LocalDate startDate,
        LocalDate endDate
) {}
