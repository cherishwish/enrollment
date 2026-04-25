package com.sowon.enrollment.domain.waitlist;

import com.sowon.enrollment.common.response.ApiResponse;
import com.sowon.enrollment.domain.waitlist.dto.WaitlistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "대기 목록 관련 API")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @GetMapping("/my")
    @Operation(summary = "내 대기 목록 조회", description = "현재 사용자의 대기 목록을 조회합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<ApiResponse<List<WaitlistResponse>>> getMyWaitlist(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(ApiResponse.ok(waitlistService.getMyWaitlist(userId)));
    }

    @DeleteMapping("/{waitlistId}")
    @Operation(summary = "대기 취소", description = "대기 목록에서 본인 항목을 삭제합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "대기 취소 성공")
    public ResponseEntity<Void> cancelWaitlist(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String waitlistId) {
        waitlistService.cancelWaitlist(userId, waitlistId);
        return ResponseEntity.noContent().build();
    }
}
