package com.sowon.enrollment.domain.enrollment;

import com.sowon.enrollment.common.exception.BusinessException;
import com.sowon.enrollment.domain.course.Course;
import com.sowon.enrollment.domain.course.CourseRepository;
import com.sowon.enrollment.domain.course.CourseStatus;
import com.sowon.enrollment.domain.user.User;
import com.sowon.enrollment.domain.user.UserRepository;
import com.sowon.enrollment.domain.user.UserRole;
import com.sowon.enrollment.domain.waitlist.WaitlistRepository;
import com.sowon.enrollment.infra.redis.DistributedLockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
class EnrollmentConcurrencyTest {

    @Autowired private EnrollmentService enrollmentService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private WaitlistRepository waitlistRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private StringRedisTemplate stringRedisTemplate;
    @MockitoBean private DistributedLockService distributedLockService;

    private static final int CAPACITY  = 10;
    private static final int TOTAL     = 30;
    private static final String COURSE_ID = "concur-course-01";

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        locks.clear();

        when(distributedLockService.tryLock(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
            try {
                if (lock.tryLock(3, TimeUnit.SECONDS)) return key + "-val";
                throw new BusinessException("LOCK_ACQUISITION_FAILED", "timeout: " + key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("LOCK_INTERRUPTED", "interrupted");
            }
        });
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            ReentrantLock lock = locks.get(key);
            if (lock != null && lock.isHeldByCurrentThread()) lock.unlock();
            return null;
        }).when(distributedLockService).unlock(anyString(), anyString());

        for (int i = 1; i <= TOTAL; i++) {
            String uid = "concur-user-" + i;
            if (userRepository.findById(uid).isEmpty()) {
                userRepository.save(User.create(uid, "학생" + i,
                        "concur" + i + "@test.com", UserRole.STUDENT));
            }
        }
        String creatorId = "concur-creator-01";
        if (userRepository.findById(creatorId).isEmpty()) {
            userRepository.save(User.create(creatorId, "강사",
                    "concur-creator@test.com", UserRole.CREATOR));
        }
        Course course = Course.create(COURSE_ID, creatorId, "동시성 테스트 강의", "설명",
                10_000, CAPACITY, LocalDate.now(), LocalDate.now().plusDays(30));
        course.changeStatus(CourseStatus.OPEN);
        courseRepository.save(course);
    }

    @AfterEach
    void tearDown() {
        waitlistRepository.deleteAll();
        enrollmentRepository.findByClassId(COURSE_ID).forEach(enrollmentRepository::delete);
        courseRepository.findById(COURSE_ID).ifPresent(courseRepository::delete);
    }

    @Test
    @DisplayName("13. 30명 동시 신청, 정원 10명 → 정확히 10명 ENROLLMENT, 20명 WAITLIST")
    void concurrent30Users_only10Enrolled() throws InterruptedException {
        List<String> userIds = new ArrayList<>();
        for (int i = 1; i <= TOTAL; i++) userIds.add("concur-user-" + i);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(TOTAL);
        ExecutorService executor  = Executors.newFixedThreadPool(TOTAL);
        List<Exception> errors    = Collections.synchronizedList(new ArrayList<>());

        for (String userId : userIds) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(userId, COURSE_ID);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        long enrollCount = enrollmentRepository.findByClassId(COURSE_ID).stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.PENDING)
                .count();
        long waitlistCount = waitlistRepository.findAll().stream()
                .filter(w -> w.getClassId().equals(COURSE_ID))
                .count();

        assertThat(errors).as("예상치 못한 예외 발생: %s", errors).isEmpty();
        assertThat(enrollCount).as("ENROLLMENT 수는 정원과 동일해야 함").isEqualTo(CAPACITY);
        assertThat(waitlistCount).as("WAITLIST 수는 초과 인원과 동일해야 함").isEqualTo(TOTAL - CAPACITY);
    }
}
