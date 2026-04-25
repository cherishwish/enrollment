package com.sowon.enrollment.common.exception;

public class AlreadyInWaitlistException extends BusinessException {
    public AlreadyInWaitlistException(String userId, String classId) {
        super("ALREADY_IN_WAITLIST", "User " + userId + " is already in waitlist for class: " + classId);
    }
}
