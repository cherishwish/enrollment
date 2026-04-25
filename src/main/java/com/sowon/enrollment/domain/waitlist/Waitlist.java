package com.sowon.enrollment.domain.waitlist;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "waitlists",
        uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waitlist {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "class_id", nullable = false, length = 36)
    private String classId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false)
    private Integer waitingOrder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Waitlist create(String id, String classId, String userId, Integer waitingOrder) {
        Waitlist waitlist = new Waitlist();
        waitlist.id = id;
        waitlist.classId = classId;
        waitlist.userId = userId;
        waitlist.waitingOrder = waitingOrder;
        return waitlist;
    }
}
