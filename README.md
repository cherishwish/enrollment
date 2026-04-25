# 수강 신청 시스템

크리에이터가 강의를 개설하고, 수강생이 신청·결제·취소하는 흐름을 처리하는 백엔드 API 서버입니다.
동시에 여러 사용자가 마지막 자리에 신청하는 상황을 Redis 분산 락으로 제어하며, 정원 초과 시 대기열에 자동 등록되는 구조를 포함합니다.

---

## 기술 스택

- **Language** : Java 21
- **Framework** : Spring Boot 3.x
- **Database** : H2 (in-memory)
- **Cache / Lock** : Redis (Lettuce)
- **API 문서** : Swagger (springdoc-openapi)

과제 특성상 빠른 실행 환경위해 H2를 선택했습니다.
`application.yml`에서 datasource 설정을 분리해두어 MySQL / PostgreSQL로 교체가 가능합니다.

---

## 실행 방법

Redis가 로컬에 실행 중이어야 합니다.

```bash
# macOS 기준
brew install redis
brew services start redis
```

```bash
git clone https://github.com/cherishwish/enrollment.git
cd enrollment
./gradlew bootRun
```

앱 실행 시 `data.sql`을 통해 초기 유저 데이터가 자동으로 생성됩니다.

| ID | 이름 | 역할 |
|----|------|------|
| user-creator-01 | 김강사 | CREATOR |
| user-student-01 | 학생A | STUDENT |
| user-student-02 | 학생B | STUDENT |
| user-student-03 | 학생C | STUDENT |

Swagger UI: `http://localhost:8080/swagger-ui.html`

H2 Console: `http://localhost:8080/h2-console`

---

## API 목록

모든 요청은 `X-User-Id` 헤더로 사용자를 식별합니다. Spring Security는 사용하지 않습니다.

모든 응답은 공통 포맷으로 반환됩니다.

```json
{ "success": true, "data": { } }
{ "success": false, "error": { "code": "COURSE_NOT_OPEN", "message": "모집 중인 강의가 아닙니다." } }
```

### Course (강의)

```
POST   /api/courses                           강의 등록 (CREATOR만)
GET    /api/courses                           강의 목록 조회 (?status=OPEN 필터 가능)
GET    /api/courses/{courseId}                강의 상세 조회
PATCH  /api/courses/{courseId}/status         강의 상태 변경 (CREATOR + 본인 강의만)
GET    /api/courses/{courseId}/enrollments    수강생 목록 조회 (CREATOR + 본인 강의만)
```

### Enrollment (수강 신청)

```
POST   /api/enrollments                      수강 신청 (정원 초과 시 대기열 자동 등록)
PATCH  /api/enrollments/{id}/confirm         결제 확정 (PENDING → CONFIRMED)
PATCH  /api/enrollments/{id}/cancel          수강 취소 (7일 이내 가능)
GET    /api/enrollments/my                   내 수강 신청 목록
```

### Waitlist (대기열)

```
GET    /api/waitlist/my                      내 대기 목록
DELETE /api/waitlist/{waitlistId}            대기 취소
```

---

## 데이터 모델

### 강의 상태 전이

```
DRAFT → OPEN → CLOSED
             ↑______| (재오픈 가능)
DRAFT → CLOSED 는 불가
```

### 수강 신청 상태 전이

```
PENDING → CONFIRMED → CANCELLED
PENDING → CANCELLED
```

CONFIRMED 상태에서의 취소는 `confirmedAt` 기준 7일 이내만 허용됩니다.

### 수강 신청 흐름

```
신청 요청
├── currentCount < maxCapacity  →  ENROLLMENT 생성 (PENDING)
└── currentCount >= maxCapacity →  WAITLIST 등록

취소 발생
├── ENROLLMENT CANCELLED 처리
├── currentCount -1
└── WAITLIST 1번 자동 ENROLLMENT 전환 (PENDING)
```

---

## 요구사항 해석 및 가정

1. 인증/인가는 `X-User-Id` 헤더로 처리합니다. 과제 제약사항에 명시된 방식입니다.
2. 결제 시스템 연동은 불필요하므로 `PENDING → CONFIRMED` 상태 변경으로 대체했습니다.
3. 취소 후 재신청을 허용했습니다. `CANCELLED` 상태 레코드가 있어도 새로 신청이 가능합니다.
4. Waitlist 자동 전환은 취소 발생 시점에 즉시 처리됩니다. 대기자에게 별도 행동은 요구하지 않습니다.

---

## 설계 결정과 이유

**동시성 처리 — Redis 분산 락**

정원 체크와 신청 저장 사이에 동시 요청이 들어오면 정원 초과가 발생할 수 있습니다.
DB 비관적 락은 단일 서버에서 안전하지만, SaaS 환경에서는 다중 인스턴스 운영 가능성이 있어 분산 락이 적합하다고 판단했습니다.
이전 프로젝트(E-booki)에서 Redis 분산 락으로 팀 인원 초과 방지를 구현한 경험을 바탕으로 적용했습니다.

락 키: `lock:enrollment:{courseId}` / TTL: 3초 / 구현: Lettuce (RedisTemplate)

**상태 전이 캡슐화**

상태 검증 로직을 서비스 레이어에 두면 동일한 검증이 여러 곳에 흩어집니다.
`Course`, `Enrollment` Entity 안에 `changeStatus()`, `confirm()`, `cancel()` 메서드를 두어 전이 규칙을 도메인이 직접 관리하도록 설계했습니다.
잘못된 전이 시도 시 `InvalidStatusTransitionException`이 발생합니다.

**DTO Record 전환**

Request DTO는 한 번 생성되면 상태가 바뀔 필요가 없습니다.
`@Getter`, `@NoArgsConstructor` 대신 Java Record로 표현해 불변성을 보장하고 코드를 간결하게 유지했습니다.

**currentCount 컬럼 관리**

매번 COUNT 쿼리를 실행하는 대신 `Course` 테이블에 `currentCount`를 두고 Redis 분산 락으로 원자적으로 관리했습니다.
인기 강의에 요청이 몰렸을 때 COUNT 쿼리 부하를 줄이기 위한 선택입니다.

---

## 테스트

```bash
./gradlew test
```

총 13개 시나리오를 작성했습니다.

| 구분 | 내용 |
|------|------|
| 단위 테스트 (11개) | 상태 전이, 정원 초과, 중복 신청, 취소 기간, 대기열 자동 전환 등 |
| 통합 테스트 (1개) | 신청 → 결제 → 취소 전체 플로우 |
| 동시성 테스트 (1개) | 30명 동시 신청, 정원 10명 → 정확히 10명만 ENROLLMENT 확인 (CountDownLatch) |

---

## 미구현 / 제약사항

- 실제 결제 시스템 연동 없음 (상태 변경으로 대체)
- 실제 알림 발송 없음 (대기열 자동 전환 시 알림 미구현)
- 운영 환경 배포 설정 없음 (H2 in-memory 사용)

---

## AI 활용 범위

본 과제에서 Claude (claude.ai 및 Claude Code)를 다음 범위에서 활용했습니다.

**AI가 담당한 부분**

- Entity, Repository 등 보일러플레이트 코드 초안 생성
- Swagger 어노테이션 추가
- 테스트 코드 초안 생성

**직접 설계하고 결정한 부분**

- ERD 및 도메인 구조 설계
- 상태 전이 규칙 정의 (허용/차단 케이스 직접 결정)
- 동시성 처리 방식 선택 (Redis 분산 락 선택 이유)
- 예외 계층 구조 설계 및 HTTP 상태코드 매핑
- 테스트 시나리오 목록 직접 작성 후 검증
- AI 생성 코드 전체 검토 및 수정

AI가 생성한 코드는 그대로 사용하지 않고, 설계 의도에 맞게 수정하고 직접 실행하여 동작을 검증했습니다.
