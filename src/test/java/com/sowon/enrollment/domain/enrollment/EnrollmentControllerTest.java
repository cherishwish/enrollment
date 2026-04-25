package com.sowon.enrollment.domain.enrollment;

import com.sowon.enrollment.infra.redis.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrollmentControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private DistributedLockService distributedLockService;

    @BeforeEach
    void setUp() {
        when(distributedLockService.tryLock(anyString())).thenReturn("test-lock");
    }

    @Test
    @DisplayName("12. 수강 신청 전체 플로우 (신청 → 결제 → 취소)")
    @SuppressWarnings("unchecked")
    void fullEnrollmentFlow() {
        String base = "http://localhost:" + port;

        HttpHeaders creatorHeaders = headers("user-creator-01");
        HttpHeaders studentHeaders = headers("user-student-01");

        Map<String, Object> createClassBody = Map.of(
                "title", "통합테스트 강의",
                "description", "강의 설명",
                "price", 50_000,
                "maxCapacity", 5,
                "startDate", "2026-09-01",
                "endDate", "2026-11-30"
        );
        ResponseEntity<Map> createResp = restTemplate.exchange(
                base + "/api/classes", HttpMethod.POST,
                new HttpEntity<>(createClassBody, creatorHeaders), Map.class);

        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> classData = (Map<String, Object>) createResp.getBody().get("data");
        String classId = (String) classData.get("id");
        assertThat(classData.get("status")).isEqualTo("DRAFT");

        ResponseEntity<Map> openResp = restTemplate.exchange(
                base + "/api/classes/" + classId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "OPEN"), creatorHeaders), Map.class);

        assertThat(openResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?,?>) openResp.getBody().get("data")).get("status")).isEqualTo("OPEN");

        ResponseEntity<Map> enrollResp = restTemplate.exchange(
                base + "/api/enrollments", HttpMethod.POST,
                new HttpEntity<>(Map.of("classId", classId), studentHeaders), Map.class);

        assertThat(enrollResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> enrollData = (Map<String, Object>) enrollResp.getBody().get("data");
        String enrollmentId = (String) enrollData.get("id");
        assertThat(enrollData.get("status")).isEqualTo("PENDING");

        ResponseEntity<Map> confirmResp = restTemplate.exchange(
                base + "/api/enrollments/" + enrollmentId + "/confirm", HttpMethod.PATCH,
                new HttpEntity<>(null, studentHeaders), Map.class);

        assertThat(confirmResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?,?>) confirmResp.getBody().get("data")).get("status")).isEqualTo("CONFIRMED");

        ResponseEntity<Map> cancelResp = restTemplate.exchange(
                base + "/api/enrollments/" + enrollmentId + "/cancel", HttpMethod.PATCH,
                new HttpEntity<>(null, studentHeaders), Map.class);

        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?,?>) cancelResp.getBody().get("data")).get("status")).isEqualTo("CANCELLED");
    }

    private HttpHeaders headers(String userId) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-User-Id", userId);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
