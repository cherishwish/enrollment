package com.sowon.enrollment.common.exception;

public class CourseNotFoundException extends BusinessException {
    public CourseNotFoundException(String courseId) {
        super("COURSE_NOT_FOUND", "Course not found: " + courseId);
    }
}
