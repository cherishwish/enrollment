package com.sowon.enrollment.common.response;

public record ApiErrorResponse(
        boolean success,
        ErrorResponse error
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorResponse(code, message));
    }
}
