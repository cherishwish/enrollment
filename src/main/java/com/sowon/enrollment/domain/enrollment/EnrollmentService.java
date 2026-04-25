package com.sowon.enrollment.domain.enrollment;

import com.sowon.enrollment.common.exception.AlreadyInWaitlistException;
import com.sowon.enrollment.common.exception.BusinessException;
import com.sowon.enrollment.common.exception.CourseNotFoundException;
import com.sowon.enrollment.common.exception.CourseNotOpenException;
import com.sowon.enrollment.common.exception.DuplicateEnrollmentException;
import com.sowon.enrollment.common.exception.EnrollmentNotFoundException;
import com.sowon.enrollment.common.exception.UserNotFoundException;
import com.sowon.enrollment.domain.course.Course;
import com.sowon.enrollment.domain.course.CourseRepository;
import com.sowon.enrollment.domain.enrollment.dto.EnrollOutcome;
import com.sowon.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.sowon.enrollment.domain.user.UserRepository;
import com.sowon.enrollment.domain.waitlist.Waitlist;
import com.sowon.enrollment.domain.waitlist.WaitlistRepository;
import com.sowon.enrollment.domain.waitlist.dto.WaitlistResponse;
import com.sowon.enrollment.infra.redis.DistributedLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final DistributedLockService distributedLockService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 수강 신청.
     * 분산 락 내부에서 REQUIRES_NEW 트랜잭션을 커밋한 뒤 락을 해제하여
     * 다음 스레드가 반드시 커밋된 currentCount를 읽도록 보장한다.
     */
    public EnrollOutcome enroll(String userId, String courseId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Course preCheck = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        if (!preCheck.isOpen()) throw new CourseNotOpenException(courseId);

        enrollmentRepository.findByClassIdAndUserIdAndStatusIn(
                courseId, userId, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
        ).ifPresent(e -> { throw new DuplicateEnrollmentException(userId, courseId); });

        String lockKey = "lock:enrollment:" + courseId;
        String lockValue = distributedLockService.tryLock(lockKey);
        try {
            return transactionTemplate.execute(status -> {
                Course course = courseRepository.findById(courseId)
                        .orElseThrow(() -> new CourseNotFoundException(courseId));

                if (!course.isFull()) {
                    Enrollment enrollment = Enrollment.create(UUID.randomUUID().toString(), courseId, userId);
                    enrollmentRepository.save(enrollment);
                    course.incrementCurrentCount();
                    return new EnrollOutcome.Enrolled(EnrollmentResponse.from(enrollment));
                } else {
                    if (waitlistRepository.existsByClassIdAndUserId(courseId, userId)) {
                        throw new AlreadyInWaitlistException(userId, courseId);
                    }
                    int nextOrder = waitlistRepository.findMaxWaitingOrderByClassId(courseId) + 1;
                    Waitlist waitlist = Waitlist.create(UUID.randomUUID().toString(), courseId, userId, nextOrder);
                    waitlistRepository.save(waitlist);
                    return new EnrollOutcome.Waitlisted(WaitlistResponse.from(waitlist));
                }
            });
        } finally {
            distributedLockService.unlock(lockKey, lockValue);
        }
    }

    @Transactional
    public EnrollmentResponse confirm(String userId, String enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Cannot confirm another user's enrollment");
        }

        enrollment.confirm();
        return EnrollmentResponse.from(enrollment);
    }

    @Transactional
    public EnrollmentResponse cancel(String userId, String enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

        if (!enrollment.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Cannot cancel another user's enrollment");
        }

        enrollment.cancel();

        Course course = courseRepository.findById(enrollment.getClassId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.getClassId()));
        course.decrementCurrentCount();

        waitlistRepository.findFirstByClassIdOrderByWaitingOrderAsc(enrollment.getClassId())
                .ifPresent(waitlist -> {
                    Enrollment promoted = Enrollment.create(
                            UUID.randomUUID().toString(),
                            waitlist.getClassId(),
                            waitlist.getUserId()
                    );
                    enrollmentRepository.save(promoted);
                    course.incrementCurrentCount();
                    waitlistRepository.delete(waitlist);
                });

        return EnrollmentResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> getMyEnrollments(String userId) {
        return enrollmentRepository.findByUserId(userId).stream()
                .map(EnrollmentResponse::from)
                .toList();
    }
}
