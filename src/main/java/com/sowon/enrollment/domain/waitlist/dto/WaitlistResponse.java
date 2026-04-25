package com.sowon.enrollment.domain.waitlist.dto;

import com.sowon.enrollment.domain.waitlist.Waitlist;

import java.time.LocalDateTime;

public record WaitlistResponse(
        String id,
        String classId,
        String userId,
        Integer waitingOrder,
        LocalDateTime createdAt
) {
    public static WaitlistResponse from(Waitlist waitlist) {
        return new WaitlistResponse(
                waitlist.getId(),
                waitlist.getClassId(),
                waitlist.getUserId(),
                waitlist.getWaitingOrder(),
                waitlist.getCreatedAt()
        );
    }
}
