package com.sowon.enrollment.common.exception;

public class CancellationPeriodExpiredException extends BusinessException {
    public CancellationPeriodExpiredException(String enrollmentId) {
        super("CANCELLATION_PERIOD_EXPIRED", "Cancellation period expired for enrollment: " + enrollmentId);
    }
}
