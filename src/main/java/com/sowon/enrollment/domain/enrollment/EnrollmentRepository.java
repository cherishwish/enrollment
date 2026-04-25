package com.sowon.enrollment.domain.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, String> {

    Optional<Enrollment> findByClassIdAndUserIdAndStatusIn(
            String classId, String userId, List<EnrollmentStatus> statuses);

    List<Enrollment> findByUserId(String userId);

    List<Enrollment> findByClassId(String classId);
}
