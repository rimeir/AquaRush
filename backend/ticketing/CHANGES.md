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

### 미완성 항목 (이슈 #9 기준)

| 항목 | 상태 |
|---|---|
| `SimulationService` — `myReservationSuccess`, `myPosition` | TODO 상태 |
| SSE 실시간 대시보드 | 미구현 |
| 프론트엔드 전체 | 미구현 |
| 테스트 (10/100/1000명 동시 접속) | 미구현 |
