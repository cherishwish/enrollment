package com.sowon.enrollment.domain.enrollment;

import com.sowon.enrollment.common.response.ApiResponse;
import com.sowon.enrollment.domain.enrollment.dto.EnrollOutcome;
import com.sowon.enrollment.domain.enrollment.dto.EnrollmentRequest;
import com.sowon.enrollment.domain.enrollment.dto.EnrollmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@Tag(name = "Enrollment", description = "수강 신청 관련 API")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @Operation(summary = "수강 신청", description = "강의를 신청합니다. 정원 초과 시 대기 목록에 등록됩니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "수강 신청 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "정원 초과 - 대기 목록 등록")
    public ResponseEntity<ApiResponse<?>> enroll(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody EnrollmentRequest request) {
        EnrollOutcome outcome = enrollmentService.enroll(userId, request.classId());
        if (outcome instanceof EnrollOutcome.Enrolled e) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(e.enrollment()));
        }
        EnrollOutcome.Waitlisted w = (EnrollOutcome.Waitlisted) outcome;
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(w.waitlist()));
    }

    @PatchMapping("/{enrollmentId}/confirm")
    @Operation(summary = "수강 확정", description = "PENDING 상태의 수강 신청을 CONFIRMED로 전환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확정 성공")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> confirm(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String enrollmentId) {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.confirm(userId, enrollmentId)));
    }

    @PatchMapping("/{enrollmentId}/cancel")
    @Operation(summary = "수강 취소", description = "수강 신청을 취소합니다. CONFIRMED 상태는 확정 후 7일 이내만 취소 가능합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> cancel(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String enrollmentId) {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.cancel(userId, enrollmentId)));
    }

    @GetMapping("/my")
    @Operation(summary = "내 수강 목록 조회", description = "현재 사용자의 수강 신청 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<ApiResponse<List<EnrollmentResponse>>> getMyEnrollments(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(enrollmentService.getMyEnrollments(userId)));
    }
}
