# Backend Changes — 프론트엔드 연동을 위한 시뮬레이션 수정

> 이슈: #14 · 브랜치: `feature/#14-simulation-backend-fix`  
> 연관: #12 (프론트엔드), #9 (시뮬레이션 백엔드 원본)

---

## 변경 파일 요약

| 파일 | 유형 | 내용 |
|---|---|---|
| `global/config/WebConfig.java` | fix | 레이트 리밋 제외 엔드포인트 추가 |
| `simulation/dto/SimulationStatusResponse.java` | feat | `totalSeats` 필드 추가 |
| `simulation/service/SimulationService.java` | feat | `getStatus()` 응답에 `totalSeats` 포함 |
| `simulation/service/BotService.java` | fix | 봇별 Redis 실시간 카운터 업데이트 |

---

## 상세 변경 내용

### 1. WebConfig.java — 레이트 리밋 제외 엔드포인트 추가

**문제**  
프론트엔드 QueueModal이 1.5초마다 `/simulation/status/{id}`를 폴링하고, SSE 스트림 `/simulation/live/{id}`를 상시 연결하는 특성상 기존 레이트 리밋(5회/분)에 걸려 `429 Too Many Requests` 오류가 발생.

**변경**
```java
.excludePathPatterns(
    "/api/health",
    "/api/swagger-ui/**",
    "/api/v3/api-docs/**",
    "/api/v1/simulation/status/**",  // 추가: 상태 폴링 제외
    "/api/v1/simulation/live/**"     // 추가: SSE 스트림 제외
);
```

---

### 2. SimulationStatusResponse.java — `totalSeats` 필드 추가

**문제**  
프론트엔드가 시뮬레이션 시작 응답에서 강좌 정원을 받아야 하나 DTO에 해당 필드가 없어 항상 기본값(20)을 사용.

**변경**
```java
@Schema(description = "총 정원")
private Integer totalSeats;
```

---

### 3. SimulationService.java — `getStatus()` 응답에 `totalSeats` 포함

**변경**  
`SimulationStatus` 빌더에 `totalSeats(course.getMaxCapacity())` 추가.

---

### 4. BotService.java — 봇별 Redis HINCRBY 실시간 카운터

**문제**  
기존 구현은 `AtomicInteger`로 메모리 내 카운트만 관리하고, 시뮬레이션 완료 시점에 일괄 저장. 그 결과 SSE로 전달되는 `successCount`, `failCount`가 봇이 모두 완료될 때까지 0으로 유지되어 QueueModal에서 실시간 현황이 보이지 않는 문제.

**변경**  
봇 1개 완료마다 즉시 Redis Hash에 증가:

```java
String simKey = "simulation:" + simulationId;

// 봇 중단(stopFlag) 시
redisTemplate.opsForHash().increment(simKey, "failCount", 1L);

// 봇 예약 성공 시
redisTemplate.opsForHash().increment(simKey, "successCount", 1L);

// 봇 예약 실패 시
redisTemplate.opsForHash().increment(simKey, "failCount", 1L);

// 봇 예외 발생 시
redisTemplate.opsForHash().increment(simKey, "failCount", 1L);
```

Redis Hash 키 구조: `simulation:{simulationId}` → `{ successCount, failCount }`  
`SimulationScheduler`가 1초마다 해당 키를 읽어 SSE로 브로드캐스트하는 기존 흐름 유지.

---

## 영향 범위

- 레이트 리밋 제외는 `/api/v1/simulation/**` 모니터링 경로에만 적용되며 다른 API 보호에는 영향 없음
- `totalSeats` 필드는 기존 응답에 추가되는 방식으로 하위 호환성 유지
- Redis HINCRBY는 `SimulationScheduler`의 기존 SSE 브로드캐스트 흐름을 그대로 활용
