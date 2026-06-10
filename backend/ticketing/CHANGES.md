# AquaRush 백엔드 변경 이력

## 대기열 ↔ 예약 시스템 연동

### 배경

이슈 #9 [Feature] 시뮬레이션 티켓팅 시스템 구축에서 대기열 시스템(`WaitingQueueService`)과 예약 시스템(`ReservationService`)이 각각 구현되어 있었으나 서로 연결되어 있지 않았습니다. 봇이 대기열을 거치지 않고 `createReservation()`을 직접 호출하여 대기열 시스템이 실제로 동작하지 않는 상태였습니다.

### 변경 흐름

**변경 전**
```
봇 → ReservationService.createReservation() → 분산 락 → 예약
```

**변경 후**
```
봇 → createReservation()
       ↓ (sessionId가 있는 경우)
       대기열 자동 진입 (처음이면 enterQueue)
       순번 확인 → 상위 10명 초과 시 "대기 중" 예외
       ↓ (BotService 재시도: 2초 간격, 최대 5회)
       순번이 10 이내가 되면 통과
       ↓
       분산 락 + 비관적 락 → 예약 생성
       ↓
       removeFromQueue() → 뒤 사람 순번 자동 앞당김
```

---

### 변경 파일

#### 1. `ReservationCreateRequest.java`

`sessionId` 필드 추가. 대기열 식별에 사용되며 선택값으로, 없으면 대기열 게이트를 건너뜁니다.

```java
// 추가
private String sessionId;
```

---

#### 2. `WaitingQueueService.java`

**`clearQueue()` 추가**

시뮬레이션 시작 시 이전 대기열을 초기화합니다.

```java
public void clearQueue(Long courseId) {
    redisTemplate.delete(getQueueKey(courseId));
}
```

**`processQueue()` 하드코딩 제거**

강좌 ID 1~10으로 고정되어 있던 스케줄러를 Redis 키 동적 스캔으로 수정했습니다.

```java
// 변경 전
for (long courseId = 1; courseId <= 10; courseId++) { ... }

// 변경 후
Set<String> keys = redisTemplate.keys("queue:course:*");
for (String key : keys) { processQueueForCourse(Long.parseLong(...)); }
```

---

#### 3. `ReservationService.java`

`WaitingQueueService` 의존성 주입 및 대기열 게이트 로직 추가.

`createReservation()` 내 변경 사항:

1. **대기열 진입** — `sessionId`가 있고 아직 대기열에 없으면 자동으로 `enterQueue()` 호출
2. **순번 확인** — 상위 10명 초과 시 `"대기 중입니다. 현재 순번: N"` 예외 throw → `BotService`의 재시도 로직에서 포착
3. **대기열 제거** — 예약 성공 후 `removeFromQueue()` 호출 → 뒤에 대기 중인 사용자 순번 앞당김

추가된 헬퍼 메서드:
```java
private void enterQueueIfAbsent(String sessionId, Long courseId)
private void checkQueueAllowed(String sessionId, Long courseId)
```

---

#### 4. `BotService.java`

`ReservationCreateRequest` 빌더에 `sessionId` 추가.

```java
// 변경 전
.userPhone("010-0000-0000")
.build();

// 변경 후
.userPhone("010-0000-0000")
.sessionId(bot.getSessionId())
.build();
```

---

#### 5. `SimulationService.java`

시뮬레이션 초기화 시 대기열 초기화 스킵 주석을 실제 호출로 교체.

```java
// 변경 전
// 원래: waitingQueueService.clearQueue(courseId);
log.info("⚠️ 대기열 초기화 생략 (clearQueue 메서드 추가 예정)");

// 변경 후
waitingQueueService.clearQueue(courseId);
```

---

---

## `/stop` API 구현

### 배경

`SimulationController`에 `/start`, `/status/{id}`, `/live/{id}`는 있었으나 `/stop`이 누락되어 있었습니다. 단순히 Redis 상태만 바꾸는 방식은 봇 스레드가 계속 실행되어 리소스를 낭비하므로, 봇 스레드를 안전하게 중단시키는 stop flag 방식으로 구현했습니다.

> `shutdownNow()`를 사용하지 않은 이유: 미실행 태스크의 `CountDownLatch.countDown()`이 호출되지 않아 `latch.await()`가 최대 10분간 블록되는 문제가 있습니다.

### 흐름

```
POST /api/v1/simulation/stop
    ↓
BotService.stopBotSimulation() — stopFlag.set(true)
    ↓ (봇 스레드들이 다음 재시도 시점에 플래그 확인 후 종료, 최대 2초)
Redis status → "STOPPED"
    ↓
SSE emitter.send("stopped") → emitter.complete()
    ↓
응답: SimulationStatusResponse (status=STOPPED)
```

### 변경 파일

#### 신규: `SimulationStopRequest.java`

```java
@NotBlank
private String simulationId;
```

#### `BotService.java`

- `ConcurrentHashMap<String, AtomicBoolean> stopFlags` 추가
- `startBotSimulation` 시그니처에 `simulationId` 추가 → 실행 시 stopFlag 등록, 종료 시 제거
- `tryReservationWithRetry`에 stopFlag 파라미터 추가 → 각 시도 전, 재시도 대기 후 체크
- `stopBotSimulation(String simulationId)` 추가 → `flag.set(true)` 신호 전송

#### `SimulationService.java`

- `startBotSimulation` 호출 시 `simulationId` 전달
- `stopSimulation(String simulationId)` 추가:
  - 존재 여부 검증
  - `botService.stopBotSimulation()` 호출
  - Redis status → `"STOPPED"`
  - emitters 맵에서 제거 후 SSE `"stopped"` 이벤트 전송 및 종료

#### `SimulationController.java`

```
POST /api/v1/simulation/stop
Body: { "simulationId": "..." }
Response: ApiResponse<SimulationStatusResponse>
```

---

---

## 시뮬레이션 유저 대기열 자동 진입 및 종료 시 대기열 정리

### 배경

Swagger 테스트 결과 두 가지 문제가 확인되었습니다.

1. **`myRank: null`** — 시뮬레이션 시작 시 유저 본인이 대기열에 진입하지 않아 순번이 조회되지 않았습니다.
2. **`queueLength: 980`** — 시뮬레이션 완료·종료 후에도 실패한 봇들이 대기열에 잔류했습니다.

### 변경 파일

#### `SimulationService.java`

**유저 대기열 자동 진입 (`createSimulation`)**

강좌 초기화 후 유저 세션을 즉시 대기열에 등록합니다. 봇보다 먼저 등록되므로 유저는 항상 앞 순번을 갖습니다.

```java
// 변경 전
// (대기열 진입 없음)

// 변경 후
waitingQueueService.enterQueue(user.getSessionId(), courseId);
```

**시뮬레이션 완료 시 대기열 정리 (`startBotSimulation` finally 블록)**

봇 정리 후 해당 강좌의 대기열 전체를 초기화합니다.

```java
// 변경 전
cleanupBots(simulationId, bots);
closeEmitter(simulationId);

// 변경 후
cleanupBots(simulationId, bots);
waitingQueueService.clearQueue(courseId);
closeEmitter(simulationId);
```

**수동 종료 시 대기열 정리 (`stopSimulation`)**

`/stop` 호출 시에도 대기열을 즉시 초기화합니다.

```java
// 변경 전
botService.stopBotSimulation(simulationId);
redisTemplate.opsForHash().put(key, "status", "STOPPED");

// 변경 후
botService.stopBotSimulation(simulationId);
Long courseId = Long.parseLong(redisTemplate.opsForHash().get(key, "courseId").toString());
waitingQueueService.clearQueue(courseId);
redisTemplate.opsForHash().put(key, "status", "STOPPED");
```

### 테스트 결과 (봇 50명, 정원 20명)

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| `myRank` (시작 직후) | null | 1 |
| `queueLength` (완료 후) | 980 | **0** |
| `successCount` (완료 후) | 0 | 20 |
| `failCount` (완료 후) | 0 | 30 |

---

---

---

## 시뮬레이션 유저 예약 성공 여부 및 순위 구현

### 배경

`SimulationStatusResponse`의 `myReservationSuccess`와 `myPosition` 필드가 각각 `false`, `null`로 하드코딩되어 있었습니다. 유저가 대기열에는 진입하지만 실제 예약 시도를 하지 않아 본인 결과를 알 수 없는 상태였습니다.

### 변경 파일

#### `SimulationController.java`

유저를 participants 맨 앞에 추가해 봇과 함께 예약 경쟁에 참여시킵니다. 대기열에서 이미 1번 순위를 갖고 있으므로 우선권이 있지만, 멀티스레드 환경에서 봇과 실제 경쟁합니다.

```java
// 변경 전
simulationService.startBotSimulation(simulationId, request.getCourseId(), bots);

// 변경 후
List<VirtualUser> participants = new ArrayList<>();
participants.add(user);  // 유저를 맨 앞에 추가
participants.addAll(bots);
simulationService.startBotSimulation(simulationId, request.getCourseId(), participants);
```

#### `SimulationService.java`

**`createSimulation()`** — `userDbId`(VirtualUser DB PK) Redis에 저장. `userId`(sessionId)는 대기열 식별용, `userDbId`는 DB 예약 조회용으로 구분합니다.

```java
redisTemplate.opsForHash().put(key, "userDbId", user.getId().toString());
```

**`getStatus()`** — DB에서 유저 예약 존재 여부와 순위를 계산합니다.

```java
boolean myReservationSuccess = userDbId != null &&
        reservationRepository.existsActiveByCourseIdAndUserId(courseId, userDbId);
Integer myPosition = null;
if (myReservationSuccess) {
    long before = reservationRepository.countReservationsBeforeUser(courseId, userDbId);
    myPosition = (int) before + 1;
}
```

#### `ReservationRepository.java`

유저보다 먼저 생성된 활성 예약 수를 조회하는 쿼리를 추가합니다. `countReservationsBeforeUser() + 1`이 유저의 최종 순위입니다.

```java
@Query("SELECT COUNT(r) FROM Reservation r " +
        "WHERE r.course.id = :courseId " +
        "AND r.status IN ('CONFIRMED', 'PENDING') " +
        "AND r.createdAt < (" +
        "  SELECT r2.createdAt FROM Reservation r2 " +
        "  WHERE r2.course.id = :courseId AND r2.userId = :userId " +
        "  AND r2.status IN ('CONFIRMED', 'PENDING')" +
        ")")
long countReservationsBeforeUser(@Param("courseId") Long courseId, @Param("userId") Long userId);
```

### 테스트 결과 (봇 50명, 정원 20명)

| 항목 | 수정 전 | 수정 후 |
|---|---|---|
| `myReservationSuccess` | false (하드코딩) | **true** |
| `myPosition` | null (하드코딩) | **4** (51명 중 4번째) |

`myPosition: 4`는 대기열 1번임에도 멀티스레드 경쟁으로 일부 봇이 앞서 예약한 결과로, 실제 티켓팅의 경쟁 상황을 반영합니다.

---

### 미완성 항목 (이슈 #9 기준)

| 항목 | 상태 |
|---|---|
| SSE 실시간 대시보드 | ✅ 구현 완료 (#9) |
| 프론트엔드 전체 | ✅ 구현 완료 (#12) |
| 테스트 (10/100/1000명 동시 접속) | ✅ 구현 완료 (#9) |

---

---

## 프론트엔드 연동을 위한 시뮬레이션 수정 (이슈 #14)

> 브랜치: `feature/#14-simulation-backend-fix`  
> 연관: #12 (프론트엔드), #9 (시뮬레이션 백엔드 원본)

### 변경 파일 요약

| 파일 | 유형 | 내용 |
|---|---|---|
| `global/config/WebConfig.java` | fix | 레이트 리밋 제외 엔드포인트 추가 |
| `simulation/dto/SimulationStatusResponse.java` | feat | `totalSeats` 필드 추가 |
| `simulation/service/SimulationService.java` | feat | `getStatus()` 응답에 `totalSeats` 포함 |
| `simulation/service/BotService.java` | fix | 봇별 Redis 실시간 카운터 업데이트 |

### 1. WebConfig.java — 레이트 리밋 제외 엔드포인트 추가

**문제**  
프론트엔드 QueueModal이 1.5초마다 `/simulation/status/{id}`를 폴링하고, SSE 스트림 `/simulation/live/{id}`를 상시 연결하는 특성상 기존 레이트 리밋(5회/분)에 걸려 `429 Too Many Requests` 오류 발생.

```java
.excludePathPatterns(
    "/api/health",
    "/api/swagger-ui/**",
    "/api/v3/api-docs/**",
    "/api/v1/simulation/status/**",  // 추가: 상태 폴링 제외
    "/api/v1/simulation/live/**"     // 추가: SSE 스트림 제외
);
```

### 2. SimulationStatusResponse.java — `totalSeats` 필드 추가

프론트엔드가 시뮬레이션 시작 응답에서 강좌 정원을 받아야 하나 DTO에 해당 필드가 없어 항상 기본값(20)을 사용하던 문제 수정.

```java
@Schema(description = "총 정원")
private Integer totalSeats;
```

### 3. BotService.java — 봇별 Redis HINCRBY 실시간 카운터

**문제**  
기존 구현은 `AtomicInteger`로 메모리 내 카운트만 관리하고 시뮬레이션 완료 시점에 일괄 저장. SSE로 전달되는 `successCount`, `failCount`가 봇이 모두 완료될 때까지 0으로 유지되어 QueueModal에서 실시간 현황 미표시.

**변경**: 봇 1개 완료마다 즉시 Redis Hash에 증가

```java
String simKey = "simulation:" + simulationId;
redisTemplate.opsForHash().increment(simKey, "successCount", 1L);  // 성공 시
redisTemplate.opsForHash().increment(simKey, "failCount", 1L);     // 실패/예외 시
```

`SimulationScheduler`가 1초마다 해당 키를 읽어 SSE로 브로드캐스트하는 기존 흐름 유지.

---

---

## 슬라이딩 윈도우 기반 유량제어로 교체 (이슈 #16)

### 배경

기존 구현은 `INCR` + TTL을 사용하는 **고정 윈도우 카운터**였습니다. 윈도우 경계에서 최대 2배 요청이 통과되는 boundary attack 취약점이 있었고, 포트폴리오 설명("슬라이딩 윈도우")과 실제 코드가 불일치했습니다.

**boundary attack 예시:**
- 00:59에 5번 요청 → 통과 (윈도우 1의 마지막)
- 01:01에 5번 요청 → 통과 (윈도우 2의 시작)
- 결과: 2초 안에 10번 통과

### 변경 내용

#### `RateLimiterService.java`

Redis Sorted Set + Lua 스크립트로 교체. 요청 시각(ms)을 score로 저장하고 "지금 기준 과거 60초"를 항상 새로 계산합니다.

```
요청 들어옴
  └─ ZREMRANGEBYSCORE: 윈도우 밖(60초 이전) 항목 제거
  └─ ZCARD: 현재 윈도우 내 요청 수 확인
  └─ count < limit → ZADD로 현재 요청 추가 → 허용
  └─ count >= limit → 거부 (429)
```

Lua 스크립트로 세 연산을 원자적으로 처리해 race condition 방지.

`StringRedisTemplate` 사용: 기존 `RedisTemplate<String, Object>`는 Jackson 직렬화로 Lua ARGV가 JSON 문자열로 변환되는 문제가 있어 교체.

#### `RateLimitInterceptor.java`

`getRemainingRequests`, `getTimeUntilReset` 메서드에 `windowSeconds` 파라미터 추가.

`getTimeUntilReset`의 의미 변경:
- 고정 윈도우: TTL (윈도우 전체 리셋까지 남은 시간)
- 슬라이딩 윈도우: 가장 오래된 요청이 윈도우 밖으로 나갈 때까지 남은 시간 (`ZRANGE key 0 0 WITHSCORES`로 조회)

---

---

## 수강신청 페이지 재설계 연동 (이슈 #22, PR #24)

> 브랜치: `feature/#22-registration-page-redesign`

### 변경 파일

#### `CourseController.java` + `CourseRepository.java`

강좌 검색 API 파라미터 확장 및 신규 엔드포인트 추가.

```
GET /api/v1/courses/search?centerId=&categoryId=&level=&targetAudience=&courseName=&instructor=
GET /api/v1/courses/random        — 활성 강좌 1개 랜덤 반환
GET /api/v1/centers               — 전체 센터 목록
GET /api/v1/categories            — 전체 카테고리 목록
```

`findRandomActiveCourse()`: 초기에는 전체 ACTIVE 강좌 대상이었으나, 이후 수켓팅 시나리오에 맞게 수영 + 성인/청소년 강좌로 제한 (이슈 #27에서 변경).

#### `SimulationStatusResponse.java`

`startSimulation` 응답에서 강좌 메타 필드 누락으로 프론트엔드가 별도 API를 호출해야 했던 문제 해결.

```java
// 추가 필드
private String courseName;
private String centerName;
private String weekdays;
private String timeSlot;
private String level;
private String targetAudience;
```

#### `SimulationService.java`

`createSimulation()` 응답 빌더에 강좌 메타 필드 포함.

---

---

## 시뮬레이션 버그 수정 (이슈 #23 / #25)

> 직접 커밋 (`205c660`) — PR #26은 동일 변경사항이 이미 main에 포함되어 CLOSED

### 문제

1. **유저 항상 예약 성공**: `createSimulation()`에서 유저를 봇보다 먼저 대기열에 넣어 항상 1번 순위 → 정원 내이면 100% 성공
2. **봇 대기열 잔류**: 시뮬레이션 완료 후에도 실패 봇들이 대기열에 남아 다음 시뮬레이션에서 `queueLength`가 비정상적으로 높은 문제
3. **AccessQueueOverlay 미표시**: 시뮬레이션 시작 전 새로고침 시 `showAccessQueue` 조건에 `!!currentSimId`가 포함되어 오버레이가 표시되지 않던 문제 (프론트엔드 수정)

### 변경 내용

#### `SimulationService.java`

유저 대기열 선점 진입 코드 제거. 유저는 `/reserve` API(이슈 #27)로 별도 예약.

```java
// 제거
waitingQueueService.enterQueue(user.getSessionId(), courseId);
```

#### `BotService.java`

**봇 공정 경쟁 보장**: 스레드 풀 실행 전 전체 봇을 동시에 대기열에 진입시켜 임의의 순위 배정.

```java
// 변경 전: 각 봇 스레드 내부에서 enterQueue → 스레드 스케줄링 순서에 따라 앞 봇이 항상 낮은 순번
// 변경 후: 스레드 시작 전 일괄 진입
for (VirtualUser bot : bots) {
    waitingQueueService.enterQueue(bot.getSessionId(), courseId);
}
executor.submit(() -> tryReservationWithRetry(...));
```

**영구 실패 봇 즉시 대기열 제거**: 정원 초과 또는 중복 예약으로 재시도 불가 판정 시 즉시 `removeFromQueue()`.

```java
if (isTerminalFailure(e)) {
    waitingQueueService.removeFromQueue(bot.getSessionId(), courseId);
    break;
}
```

---

---

## 수강신청 UX 전면 개편 — 유저 직접 예약 플로우 (이슈 #27, PR #28)

> 브랜치: `feature/#27-ux-overhaul`

### 변경 배경

기존 구조에서는 유저가 봇과 함께 시뮬레이션 참가자 목록에 포함되어 자동으로 예약이 처리되었습니다. 이를 "장바구니 → 결제 → 결과" 실제 수강신청 플로우로 전환하면서 유저 예약을 별도 API로 분리했습니다.

### 변경 파일

#### `UserReserveResponse.java` (신규)

```java
@Getter @Builder @AllArgsConstructor @NoArgsConstructor
public class UserReserveResponse {
    private boolean reserved;
    private String failReason;
    private Integer myPosition;
    private String courseName;
    private Integer totalParticipants;
    private Integer successCount;
    private Integer failCount;
}
```

#### `SimulationController.java`

유저를 봇 참가자 목록에서 완전 제거. `/reserve` 엔드포인트 추가.

```java
// 변경 전
List<VirtualUser> participants = new ArrayList<>();
participants.add(user);
participants.addAll(bots);
simulationService.startBotSimulation(simulationId, courseId, participants);

// 변경 후
simulationService.startBotSimulation(simulationId, courseId, bots);

// 추가
@PostMapping("/{simulationId}/reserve")
public ResponseEntity<ApiResponse<UserReserveResponse>> reserveForUser(
        @PathVariable String simulationId) { ... }
```

#### `SimulationService.reserveForUser()` (신규)

`@Transactional` 없음 — 내부에서 호출하는 `createReservation()`이 자체 트랜잭션을 관리하기 때문. `@Transactional`을 붙이면 예외를 catch해도 Spring이 트랜잭션을 rollback-only로 마킹하여 `UnexpectedRollbackException` 발생.

```
Redis에서 유저 정보 조회
  └─ 이미 예약된 경우 → reserved=true 즉시 반환
  └─ createReservation() 호출 (sessionId=null로 대기열 게이트 우회)
       ├─ 성공 → myPosition, 통계 계산 후 반환
       └─ IllegalStateException (정원 초과) → reserved=false, failReason 반환
```

#### `CourseRepository.findRandomActiveCourse()` 변경

수켓팅 시나리오에 맞게 수영 + 성인/청소년 강좌만 반환하도록 네이티브 쿼리 변경.

```sql
SELECT c.* FROM courses c
JOIN categories cat ON c.category_id = cat.id
WHERE c.status = 'ACTIVE'
  AND cat.name = '수영'
  AND c.target_audience = '성인/청소년'
ORDER BY RAND() LIMIT 1
