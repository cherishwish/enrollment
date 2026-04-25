package com.sowon.enrollment.common.exception;

public class DuplicateEnrollmentException extends BusinessException {
    public DuplicateEnrollmentException(String userId, String classId) {
        super("DUPLICATE_ENROLLMENT", "User " + userId + " already enrolled in class: " + classId);
    }
}
