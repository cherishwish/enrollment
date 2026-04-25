package com.sowon.enrollment.common.exception;

public class CourseNotOpenException extends BusinessException {
    public CourseNotOpenException(String courseId) {
        super("COURSE_NOT_OPEN", "Course is not open for enrollment: " + courseId);
    }
}
