package com.sowon.enrollment.common.exception;

public class InvalidStatusTransitionException extends BusinessException {
    public InvalidStatusTransitionException(String from, String to) {
        super("INVALID_STATUS_TRANSITION", "Invalid status transition: " + from + " → " + to);
    }
}
