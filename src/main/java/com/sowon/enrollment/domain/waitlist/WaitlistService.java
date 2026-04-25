package com.sowon.enrollment.domain.waitlist;

import com.sowon.enrollment.common.exception.BusinessException;
import com.sowon.enrollment.domain.waitlist.dto.WaitlistResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;

    public List<WaitlistResponse> getMyWaitlist(String userId) {
        return waitlistRepository.findByUserId(userId).stream()
                .map(WaitlistResponse::from)
                .toList();
    }

    @Transactional
    public void cancelWaitlist(String userId, String waitlistId) {
        Waitlist waitlist = waitlistRepository.findById(waitlistId)
                .orElseThrow(() -> new BusinessException("WAITLIST_NOT_FOUND", "Waitlist not found: " + waitlistId));

        if (!waitlist.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Cannot cancel another user's waitlist");
        }

        waitlistRepository.delete(waitlist);
    }
}
