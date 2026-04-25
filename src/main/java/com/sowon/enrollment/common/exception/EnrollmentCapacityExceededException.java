package com.sowon.enrollment.common.exception;

public class EnrollmentCapacityExceededException extends BusinessException {
    public EnrollmentCapacityExceededException(String classId) {
        super("CAPACITY_EXCEEDED", "Class capacity exceeded: " + classId);
    }
}
