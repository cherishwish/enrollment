package com.sowon.enrollment.common.exception;

import com.sowon.enrollment.common.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            UserNotFoundException.class,
            CourseNotFoundException.class,
            EnrollmentNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleNotFound(BusinessException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({
            CourseNotOpenException.class,
            EnrollmentCapacityExceededException.class,
            InvalidStatusTransitionException.class,
            DuplicateEnrollmentException.class,
            AlreadyInWaitlistException.class,
            CancellationPeriodExpiredException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_SERVER_ERROR", e.getMessage()));
    }
}
