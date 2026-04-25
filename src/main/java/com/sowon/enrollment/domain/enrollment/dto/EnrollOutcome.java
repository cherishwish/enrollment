package com.sowon.enrollment.domain.enrollment.dto;

import com.sowon.enrollment.domain.waitlist.dto.WaitlistResponse;

public sealed interface EnrollOutcome permits EnrollOutcome.Enrolled, EnrollOutcome.Waitlisted {

    record Enrolled(EnrollmentResponse enrollment) implements EnrollOutcome {}

    record Waitlisted(WaitlistResponse waitlist) implements EnrollOutcome {}
}
