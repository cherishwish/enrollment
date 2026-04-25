package com.sowon.enrollment.domain.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, String> {

    boolean existsByClassIdAndUserId(String classId, String userId);

    Optional<Waitlist> findFirstByClassIdOrderByWaitingOrderAsc(String classId);

    List<Waitlist> findByUserId(String userId);

    @Query("SELECT COALESCE(MAX(w.waitingOrder), 0) FROM Waitlist w WHERE w.classId = :classId")
    int findMaxWaitingOrderByClassId(@Param("classId") String classId);
}
