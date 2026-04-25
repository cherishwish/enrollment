package com.sowon.enrollment.common.exception;

public class EnrollmentNotFoundException extends BusinessException {
    public EnrollmentNotFoundException(String enrollmentId) {
        super("ENROLLMENT_NOT_FOUND", "Enrollment not found: " + enrollmentId);
    }
}
