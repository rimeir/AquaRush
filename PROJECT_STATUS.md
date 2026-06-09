# AquaRush — 프로젝트 현황

> 마지막 업데이트: 2026-06-10

---

## 이슈 현황

| 이슈 | 제목 | 상태 | 비고 |
|---|---|---|---|
| #1 | 프론트엔드 화면 초기설정 | ✅ CLOSED | 정적 HTML |
| #3 | 백엔드 초기 설정 | ✅ CLOSED | Spring Boot, MySQL, Redis 설정 |
| #5 | 강좌 조회 시스템 구축 | ✅ CLOSED | 검색 API, 더미 데이터 |
| #7 | 예약 시스템 구축 | ✅ CLOSED | 이중 동시성 제어 |
| #9 | 시뮬레이션 티켓팅 시스템 구축 | 🔴 OPEN (미닫힘) | 백엔드 완료, 부하 테스트 완료 |
| #12 | 시뮬레이션 프론트엔드 구축 | 🔴 OPEN (미닫힘) | React 프론트엔드 완료 |
| #14 | 프론트엔드 연동 백엔드 수정 | 🔴 OPEN (미닫힘) | 레이트 리밋, 실시간 카운터 수정 |

> ⚠️ 이슈 #9, #12, #14는 PR이 머지됐지만 GitHub 이슈가 닫히지 않은 상태입니다.

---

## 구현 완료 기능

### 백엔드

#### 강좌/센터/카테고리
- 강좌 상세 조회, 다중 조건 검색 (센터/카테고리/강사/요일)
- 정원 증감 및 상태 자동 전환 (ACTIVE → FULL)
- 시뮬레이션용 정원 초기화

#### 예약 시스템
- **이중 동시성 제어**: Redisson 분산 락 + DB 비관적 락 (SELECT FOR UPDATE)
- 중복 예약 방지, 정원 초과 완벽 차단
- 예약 생성/조회/취소, Fetch Join으로 N+1 해결

#### 유량제어
- 슬라이딩 윈도우 알고리즘, Redis 원자 연산
- 5회/분 제한, HTTP 429 응답
- SSE/상태 조회 엔드포인트 제외 처리 (#14)

#### 대기열 (Redis Sorted Set)
- `enterQueue` / `checkQueueAllowed` / `removeFromQueue` / `clearQueue`
- 상위 10명 이내 진입 허용, 1초마다 스케줄러 처리
- 강좌 ID 동적 스캔 (하드코딩 제거)

#### 시뮬레이션 엔진
- 가상 유저 / 봇 생성 (VirtualUser, BotService)
- 멀티스레드: `min(봇 수, 100)` ThreadPool + CountDownLatch
- 재시도 전략: 최대 5회, 2초 간격, 정원 초과 즉시 포기
- stop flag로 안전한 스레드 종료 (`/stop` API)
- 봇별 완료 시 즉시 Redis HINCRBY (#14)
- 유저 대기열 자동 진입 및 예약 결과 추적
- `myReservationSuccess`, `myPosition` 실제 DB 조회로 계산

#### SSE 실시간 스트림
- `SimulationScheduler` 1초 브로드캐스트
- `successCount`, `failCount`, `queueLength`, `myRank`, `remainingSeats` 전송

### 프론트엔드

#### 페이지
- **StartPage**: 닉네임, 난이도(Easy/Normal/Hard), 봇 수 슬라이더
- **RegistrationPage**: 9시 자동 봇 시작, openOnMount 패턴, 버튼 활성화 제어
- **ResultPage**: 성공/실패 결과, 재시도 시 세션 초기화
- **CartPage**: 예약 강좌 목록, 결제 금액 요약
- **CheckoutPage**: 결제 수단 선택, 약관 동의

#### 컴포넌트
- **AccessQueueOverlay**: 새로고침 시 5초 유량제어 팝업
- **QueueModal**: SSE + 1.5초 폴링 이중 실시간 업데이트
- **AquaHeader**: 4단계 진행 표시, 가상 시계
- StatCard, QueueBar (대시보드용)

#### 가상 시계
- 08:59:30 → 09:00:00, 실시간 1초 증가
- sessionStorage로 새로고침 후 시간 연속성 유지

---

## 알려진 문제 / 미완성

### 🔴 확인 필요 (E2E 미검증)

| # | 문제 | 위치 | 설명 |
|---|---|---|---|
| 1 | 전체 E2E 플로우 미검증 | 전체 | 백엔드 + 프론트엔드를 함께 실행해서 실제 흐름 테스트 필요 |
| 2 | 프론트엔드 UX 흐름 이상 | RegistrationPage | 사용자 보고: "프로세스가 이상함" — 구체적 재현 후 수정 필요 |

### 🟡 미구현 / 개선 필요

| # | 항목 | 우선순위 | 설명 |
|---|---|---|---|
| 3 | JWT 인증 | 낮음 | 현재 `userId`를 쿼리 파라미터로 직접 받음. 포트폴리오 목적이면 생략 가능 |
| 4 | `deleteByCourseId` 최적화 | 낮음 | 현재 findAll + deleteAll 방식 → 벌크 쿼리로 개선 가능 |
| 5 | 프론트엔드 DashboardPage 연동 | 낮음 | 컴포넌트는 있으나 라우트 미등록 |

### 🟢 완료 처리 필요

| # | 항목 |
|---|---|
| 6 | GitHub 이슈 #9, #12, #14 닫기 |
| 7 | `frontend/README.md` 내용 갱신 (정적 HTML → React) |

---

## 다음 작업 우선순위

```
1. GitHub 이슈 #9, #12, #14 닫기
2. 백엔드 + 프론트엔드 로컬 E2E 실행 및 플로우 검증
3. UX 이상 문제 재현 및 수정
4. (선택) DashboardPage 라우트 연결
5. (선택) JWT 인증 추가
```

---

## 브랜치 / PR 히스토리

| PR | 브랜치 | 내용 | 상태 |
|---|---|---|---|
| #2 | `feature/#1-frontend-setting` | 프론트엔드 초기 설정 | ✅ merged |
| #4 | `feature/#3-backend-setting` | 백엔드 초기 설정 | ✅ merged |
| #6 | `feature/#5-course-api` | 강좌 API | ✅ merged |
| #8 | `feature/#7-reservation-system` | 예약 시스템 | ✅ merged |
| #11 | `feature/#9-load-test` | 부하 테스트 | ✅ merged |
| #13 | `feature/#12-simulation-frontend` | 프론트엔드 구축 | ✅ merged |
| #15 | `feature/#14-simulation-backend-fix` | 백엔드 수정 | ✅ merged |

---

## 로컬 실행 체크리스트

- [ ] Docker Desktop 실행
- [ ] `docker-compose up -d` (Redis + Redis Commander)
- [ ] MySQL `aquarush` 데이터베이스 생성
- [ ] `cd backend/ticketing && ./gradlew bootRun`
- [ ] `cd frontend && npm run dev`
- [ ] http://localhost:5173 접속
