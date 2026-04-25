package com.sowon.enrollment.domain.enrollment;

import com.sowon.enrollment.common.exception.CancellationPeriodExpiredException;
import com.sowon.enrollment.common.exception.CourseNotOpenException;
import com.sowon.enrollment.common.exception.DuplicateEnrollmentException;
import com.sowon.enrollment.common.exception.InvalidStatusTransitionException;
import com.sowon.enrollment.domain.course.Course;
import com.sowon.enrollment.domain.course.CourseRepository;
import com.sowon.enrollment.domain.course.CourseStatus;
import com.sowon.enrollment.domain.enrollment.dto.EnrollOutcome;
import com.sowon.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.sowon.enrollment.domain.user.User;
import com.sowon.enrollment.domain.user.UserRepository;
import com.sowon.enrollment.domain.user.UserRole;
import com.sowon.enrollment.domain.waitlist.Waitlist;
import com.sowon.enrollment.domain.waitlist.WaitlistRepository;
import com.sowon.enrollment.infra.redis.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private DistributedLockService distributedLockService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private EnrollmentService enrollmentService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().when(distributedLockService.tryLock(anyString())).thenReturn("test-lock");
    }

    private User student(String id) {
        return User.create(id, "학생_" + id, id + "@test.com", UserRole.STUDENT);
    }

    private Course course(String id, CourseStatus status, int max, int current) {
        Course c = Course.create(id, "creator-01", "테스트 강의", "설명", 10_000,
                max, LocalDate.now(), LocalDate.now().plusDays(30));
        setField(c, "status", status);
        setField(c, "currentCount", current);
        return c;
    }

    private Enrollment pendingEnrollment(String id, String courseId, String userId) {
        return Enrollment.create(id, courseId, userId);
    }

    private Enrollment confirmedEnrollment(String id, String courseId, String userId, int daysAgo) {
        Enrollment e = Enrollment.create(id, courseId, userId);
        setField(e, "status", EnrollmentStatus.CONFIRMED);
        setField(e, "confirmedAt", LocalDateTime.now().minusDays(daysAgo));
        return e;
    }

    private Enrollment cancelledEnrollment(String id, String courseId, String userId) {
        Enrollment e = Enrollment.create(id, courseId, userId);
        setField(e, "status", EnrollmentStatus.CANCELLED);
        return e;
    }

    private void setField(Object obj, String name, Object value) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** enroll()의 공통 선행 조건 설정 */
    private void givenOpenCourse(String userId, String courseId, Course c) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(student(userId)));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(c));
        when(enrollmentRepository.findByClassIdAndUserIdAndStatusIn(
                eq(courseId), eq(userId), anyList())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("1. OPEN 강의 정상 신청 → ENROLLMENT(PENDING) 생성")
    void enroll_openCourse_createsEnrollment() {
        Course c = course("course-1", CourseStatus.OPEN, 10, 5);
        givenOpenCourse("user-1", "course-1", c);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EnrollOutcome outcome = enrollmentService.enroll("user-1", "course-1");

        assertThat(outcome).isInstanceOf(EnrollOutcome.Enrolled.class);
        EnrollmentResponse resp = ((EnrollOutcome.Enrolled) outcome).enrollment();
        assertThat(resp.status()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(resp.classId()).isEqualTo("course-1");
        verify(distributedLockService).unlock(anyString(), eq("test-lock"));
    }

    @Test
    @DisplayName("2. DRAFT 강의 신청 시도 → CourseNotOpenException")
    void enroll_draftCourse_throwsCourseNotOpen() {
        Course c = course("course-1", CourseStatus.DRAFT, 10, 0);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(student("user-1")));
        when(courseRepository.findById("course-1")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> enrollmentService.enroll("user-1", "course-1"))
                .isInstanceOf(CourseNotOpenException.class);
        verifyNoInteractions(distributedLockService);
    }

    @Test
    @DisplayName("3. CLOSED 강의 신청 시도 → CourseNotOpenException")
    void enroll_closedCourse_throwsCourseNotOpen() {
        Course c = course("course-1", CourseStatus.CLOSED, 10, 10);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(student("user-1")));
        when(courseRepository.findById("course-1")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> enrollmentService.enroll("user-1", "course-1"))
                .isInstanceOf(CourseNotOpenException.class);
        verifyNoInteractions(distributedLockService);
    }

    @Test
    @DisplayName("4. 정원 초과 신청 → WAITLIST 등록")
    void enroll_fullCourse_createsWaitlist() {
        Course c = course("course-1", CourseStatus.OPEN, 10, 10);
        givenOpenCourse("user-1", "course-1", c);
        when(waitlistRepository.existsByClassIdAndUserId("course-1", "user-1")).thenReturn(false);
        when(waitlistRepository.findMaxWaitingOrderByClassId("course-1")).thenReturn(2);
        when(waitlistRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EnrollOutcome outcome = enrollmentService.enroll("user-1", "course-1");

        assertThat(outcome).isInstanceOf(EnrollOutcome.Waitlisted.class);
        verify(waitlistRepository).save(argThat(w -> w.getUserId().equals("user-1")));
    }

    @Test
    @DisplayName("5. 중복 신청 (PENDING 상태 존재) → DuplicateEnrollmentException")
    void enroll_pendingExists_throwsDuplicate() {
        Course c = course("course-1", CourseStatus.OPEN, 10, 5);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(student("user-1")));
        when(courseRepository.findById("course-1")).thenReturn(Optional.of(c));
        when(enrollmentRepository.findByClassIdAndUserIdAndStatusIn(
                eq("course-1"), eq("user-1"), anyList()))
                .thenReturn(Optional.of(pendingEnrollment("e-1", "course-1", "user-1")));

        assertThatThrownBy(() -> enrollmentService.enroll("user-1", "course-1"))
                .isInstanceOf(DuplicateEnrollmentException.class);
        verifyNoInteractions(distributedLockService);
    }

    @Test
    @DisplayName("6. CANCELLED 후 재신청 → 정상 처리 (새 PENDING 생성)")
    void enroll_afterCancel_succeeds() {
        Course c = course("course-1", CourseStatus.OPEN, 10, 5);
        givenOpenCourse("user-1", "course-1", c);
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EnrollOutcome outcome = enrollmentService.enroll("user-1", "course-1");

        assertThat(outcome).isInstanceOf(EnrollOutcome.Enrolled.class);
        assertThat(((EnrollOutcome.Enrolled) outcome).enrollment().status())
                .isEqualTo(EnrollmentStatus.PENDING);
    }

    @Test
    @DisplayName("7. PENDING → CONFIRMED 전환 → 정상")
    void confirm_pending_succeeds() {
        Enrollment e = pendingEnrollment("e-1", "course-1", "user-1");
        when(enrollmentRepository.findById("e-1")).thenReturn(Optional.of(e));

        EnrollmentResponse resp = enrollmentService.confirm("user-1", "e-1");

        assertThat(resp.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(resp.confirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("8. CONFIRMED → CANCELLED (7일 이내) → 정상")
    void cancel_confirmedWithin7Days_succeeds() {
        Enrollment e = confirmedEnrollment("e-1", "course-1", "user-1", 3);
        Course c = course("course-1", CourseStatus.OPEN, 10, 5);
        when(enrollmentRepository.findById("e-1")).thenReturn(Optional.of(e));
        when(courseRepository.findById("course-1")).thenReturn(Optional.of(c));
        when(waitlistRepository.findFirstByClassIdOrderByWaitingOrderAsc("course-1"))
                .thenReturn(Optional.empty());

        EnrollmentResponse resp = enrollmentService.cancel("user-1", "e-1");

        assertThat(resp.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(resp.cancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("9. CONFIRMED → CANCELLED (7일 초과) → CancellationPeriodExpiredException")
    void cancel_confirmedAfter7Days_throwsExpired() {
        Enrollment e = confirmedEnrollment("e-1", "course-1", "user-1", 8);
        when(enrollmentRepository.findById("e-1")).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> enrollmentService.cancel("user-1", "e-1"))
                .isInstanceOf(CancellationPeriodExpiredException.class);
    }

    @Test
    @DisplayName("10. CANCELLED → CONFIRMED 전환 시도 → InvalidStatusTransitionException")
    void confirm_cancelled_throwsInvalidTransition() {
        Enrollment e = cancelledEnrollment("e-1", "course-1", "user-1");
        when(enrollmentRepository.findById("e-1")).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> enrollmentService.confirm("user-1", "e-1"))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("11. 취소 발생 시 WAITLIST 1번 → 자동 ENROLLMENT 전환")
    void cancel_withWaitlist_promotesFirstEntry() {
        Enrollment e = pendingEnrollment("e-1", "course-1", "user-1");
        Course c = course("course-1", CourseStatus.OPEN, 10, 5);
        Waitlist w = Waitlist.create("w-1", "course-1", "user-2", 1);

        when(enrollmentRepository.findById("e-1")).thenReturn(Optional.of(e));
        when(courseRepository.findById("course-1")).thenReturn(Optional.of(c));
        when(waitlistRepository.findFirstByClassIdOrderByWaitingOrderAsc("course-1"))
                .thenReturn(Optional.of(w));
        when(enrollmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        enrollmentService.cancel("user-1", "e-1");

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("user-2");
        assertThat(captor.getValue().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        verify(waitlistRepository).delete(w);
    }
}
