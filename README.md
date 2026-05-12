# AquaRush - 수영 수강신청 연습 시뮬레이션

> 매번 실패하는 수켓팅... **AquaRush**로 수켓팅 연습해서 행수하자!  
> 나도 이제 수켓팅 마스터!

**AquaRush**는 수영 수강신청(수켓팅) 경쟁 환경을 재현하는 시뮬레이션 시스템입니다.  
분산 락, 유량제어, 대기열, 멀티스레드 동시성 제어 등 실제 티케팅 시스템에서 사용하는 기술을 직접 구현하고 학습합니다.

---

## 학습 목표

- 동시성 제어 (분산 락 + 비관적 락 이중 구조)
- API 유량제어 (Token Bucket 알고리즘 + Redis)
- 대기열 관리 (Redis Sorted Set)
- 대규모 동시 요청 시뮬레이션 (멀티스레드 + 재시도 로직)
- SSE 기반 실시간 통신

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5.6 |
| Database | MySQL |
| Cache / Queue | Redis (Redisson 3.24.3) |
| API 문서 | Swagger / SpringDoc OpenAPI 2.8.6 |
| Infrastructure | Docker Compose |

---

## 프로젝트 구조

```
AquaRush/
├── backend/
│   └── ticketing/
│       └── src/main/java/com/aquarush/ticketing/
│           ├── category/          # 강좌 카테고리 관리
│           ├── center/            # 수영장/센터 관리
│           ├── course/            # 강좌 관리 (비관적 락 적용)
│           ├── reservation/       # 예약 관리 (분산 락 + 비관적 락)
│           ├── lock/              # Redisson 분산 락 서비스
│           ├── ratelimit/         # API 유량제어 (Token Bucket)
│           ├── simulation/        # 시뮬레이션 세션, 봇, 가상 사용자
│           ├── waitingqueue/      # Redis 기반 대기열
│           └── global/            # 공통 설정 (Redis, Web, Async, Swagger)
├── frontend/                      # 프론트엔드 (미구현)
└── docker-compose.yml             # Redis + Redis Commander
```

---

## 구현 현황

### ✅ 완료

#### 강좌 관리 (Course)
- 강좌 상세 조회, 다중 조건 검색 (센터/카테고리/강사/요일)
- 정원 증감 및 상태 자동 전환 (ACTIVE → FULL)
- 비관적 락 적용 (`SELECT FOR UPDATE`)
- 시뮬레이션용 정원 초기화 (`resetCapacity`)

#### 예약 관리 (Reservation)
- 예약 생성 — 분산 락(Redisson) + 비관적 락(DB) 이중 동시성 제어
- 중복 예약 방지, 정원 초과 완벽 차단
- 예약 조회 (상세 / 사용자별 / 강좌별) — Fetch Join으로 N+1 해결
- 예약 취소 및 상태 관리 (PENDING → CONFIRMED → COMPLETED / CANCELLED)

#### 유량제어 (RateLimit)
- Token Bucket 알고리즘, Redis 원자 연산 기반
- 기본 제한: 1분에 5회 (`/api/**` 전체 적용)
- 응답 헤더 포함: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- 초과 시 HTTP 429 응답

#### 분산 락 (DistributedLock)
- Redisson 기반, `executeWithLock(lockKey, waitTime, leaseTime, task)` 유틸 메서드
- 예약 서비스에 통합 (락 키: `reservation:course:{courseId}`)

#### 시뮬레이션 (Simulation)
- 가상 사용자 / 봇 생성 및 자동 정리 (만료 1시간, 스케줄러 매 시간 정각)
- 멀티스레드 동시 예약 시도 (ThreadPool: min(봇 수, 100))
- 재시도 로직: 최대 5회, 2초 간격 (정원 초과 시 즉시 포기)
- 성공/실패/시도 횟수 통계 (`BotSimulationResult`)
- SSE 실시간 상태 스트림 (`/api/v1/simulation/live/{id}`)

#### 대기열 (WaitingQueue)
- Redis Sorted Set 기반 (score = 진입 시간)
- 진입, 순위 조회, 예약 허용 판단 (상위 10명)
- 스케줄러: 1초마다 대기열 처리

---

### 미구현 / TODO

#### 긴급 (연동 누락)

| # | 항목 | 위치 | 설명 |
|---|------|------|------|
| 1 | `WaitingQueueService.clearQueue()` 미구현 | `SimulationService.resetCourseForSimulation()` | 시뮬레이션 초기화 시 대기열 비워야 함 (TODO 주석 있음) |
| 2 | 대기열 ↔ 예약 흐름 미통합 | `ReservationService`, `WaitingQueueService` | 현재 대기열과 실제 예약이 연결되지 않음 |
| 3 | `ReservationRepository.deleteByCourseId()` 미구현 | `SimulationService` | 현재 findAll + deleteAll 방식, 성능 비효율 |

#### 기능 개선

| # | 항목 | 위치 | 설명 |
|---|------|------|------|
| 4 | 시뮬레이션 결과 실제화 | `SimulationService` | `myReservationSuccess`, `myPosition` 필드 값이 실제로 채워지지 않음 |
| 5 | SSE 대기열 정보 추가 | `SimulationService.streamStatus()` | 현재 봇 통계만 전송, 대기열 현황 미포함 |
| 6 | 봇별 진행 상황 추적 | `BotService` | 개별 봇 성공/실패 이유 추적 없음 |

#### 인증/보안

| # | 항목 | 설명 |
|---|------|------|
| 7 | JWT 인증 미구현 | 현재 userId를 쿼리 파라미터로 직접 받음 — 보안 취약 |
| 8 | `@AuthenticationPrincipal` 미적용 | 인증 후 userId 자동 추출 필요 |

#### 프론트엔드

| # | 항목 | 설명 |
|---|------|------|
| 9 | 시뮬레이션 UI | 강좌 선택, 봇 수 설정, 시뮬레이션 실행 화면 |
| 10 | 실시간 대시보드 | SSE 연결 후 통계 시각화 |
| 11 | 강좌 검색/예약 UI | 일반 사용자용 수강신청 화면 |

---

## API 엔드포인트

### 강좌
```
GET  /api/v1/courses/{courseId}     강좌 상세 조회
GET  /api/v1/courses/search         강좌 검색 (centerId, categoryId, courseName, instructor, weekday)
```

### 예약
```
POST   /api/v1/reservations              예약 생성
GET    /api/v1/reservations/{id}         예약 상세 조회
GET    /api/v1/reservations/my           내 예약 목록 (?userId=)
GET    /api/v1/reservations/course/{id}  강좌별 예약 목록
DELETE /api/v1/reservations/{id}         예약 취소
```

### 시뮬레이션
```
POST   /api/v1/simulation/start          시뮬레이션 시작
GET    /api/v1/simulation/status/{id}    현황 조회
GET    /api/v1/simulation/live/{id}      실시간 스트림 (SSE)
```

### 카테고리 / 센터
```
GET    /api/v1/categories
POST   /api/v1/categories
GET    /api/v1/centers
POST   /api/v1/centers
```

---

## 시뮬레이션 사용법

**1. 시뮬레이션 시작**
```json
POST /api/v1/simulation/start
{
  "courseId": 1,
  "botCount": 1000,
  "nickname": "수켓팅마스터"
}
```

**2. 실시간 모니터링 (SSE)**
```
GET /api/v1/simulation/live/{simulationId}
```

**3. 결과 예시**
```json
{
  "totalBots": 1000,
  "successCount": 20,
  "failCount": 980,
  "successRate": 2.0,
  "averageAttempts": 1.8
}
```

---

## 주요 설계 결정

### 이중 동시성 제어
```
예약 생성 요청
  → Redisson 분산 락 (여러 서버 간 동시성)
    → DB SELECT FOR UPDATE (단일 트랜잭션 내 동시성)
      → 중복 예약 확인
      → 정원 확인 및 증가
      → 예약 저장
  → 락 해제
```

### 봇 재시도 전략
```
예약 시도 (최대 5회)
  성공        → 완료
  정원 초과   → 즉시 포기 (재시도 없음)
  중복 예약   → 즉시 포기
  기타 에러   → 2초 후 재시도
```

### Redis 데이터 구조
| 용도 | 자료구조 | 키 패턴 |
|------|----------|---------|
| 대기열 | Sorted Set | `waiting:queue:{courseId}` |
| 유량제어 | String (카운터) | `rate_limit:{userId}:{window}` |
| 분산 락 | Lock (Redisson) | `reservation:course:{courseId}` |

---

## 로컬 실행

```bash
# Redis 실행
docker-compose up -d

# 애플리케이션 실행 (MySQL 별도 필요)
./gradlew bootRun
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Redis Commander: `http://localhost:8081`

---

## 브랜치 전략 및 커밋 히스토리

| 이슈 | 내용 |
|------|------|
| #7 | 예약 시스템 구현 (엔티티, 서비스, 컨트롤러) |
| #6 | 강좌 API 구현 |

현재 작업 중 (미커밋):
- 분산 락 (`lock/`)
- 유량제어 (`ratelimit/`)
- 시뮬레이션 (`simulation/`)
- 대기열 (`waitingqueue/`)
- Redis/Async/Web 설정 (`global/config/`)
- Docker Compose
