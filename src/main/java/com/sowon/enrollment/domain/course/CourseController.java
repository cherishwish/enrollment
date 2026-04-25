package com.sowon.enrollment.domain.course;

import com.sowon.enrollment.common.response.ApiResponse;
import com.sowon.enrollment.domain.course.dto.CourseCreateRequest;
import com.sowon.enrollment.domain.course.dto.CourseResponse;
import com.sowon.enrollment.domain.course.dto.CourseStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
@Tag(name = "Course", description = "강의 관련 API")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @Operation(summary = "강의 등록", description = "CREATOR 권한 사용자가 새 강의를 등록합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "강의 생성 성공")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(courseService.createCourse(userId, request)));
    }

    @GetMapping
    @Operation(summary = "강의 목록 조회", description = "상태별 강의 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getCourses(
            @RequestParam(required = false) CourseStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(courseService.getCourses(status)));
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "강의 상세 조회", description = "특정 강의의 상세 정보를 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<ApiResponse<CourseResponse>> getCourseDetail(
            @PathVariable String courseId) {
        return ResponseEntity.ok(ApiResponse.ok(courseService.getCourseById(courseId)));
    }

    @PatchMapping("/{courseId}/status")
    @Operation(summary = "강의 상태 변경", description = "강의 상태를 변경합니다. DRAFT→OPEN→CLOSED 순서로만 전이 가능합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공")
    public ResponseEntity<ApiResponse<CourseResponse>> updateStatus(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String courseId,
            @RequestBody CourseStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(courseService.updateStatus(userId, courseId, request)));
    }
}
