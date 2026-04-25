package com.sowon.enrollment.domain.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, String> {
    List<Course> findByStatus(CourseStatus status);
}
