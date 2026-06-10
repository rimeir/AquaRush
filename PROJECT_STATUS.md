# AquaRush — 프로젝트 현황

> 마지막 업데이트: 2026-06-11

---

## 이슈 현황

| 이슈 | 제목 | 상태 | 비고 |
|---|---|---|---|
| #1 | 프론트엔드 화면 초기설정 | ✅ CLOSED | 정적 HTML |
| #3 | 백엔드 초기 설정 | ✅ CLOSED | Spring Boot, MySQL, Redis 설정 |
| #5 | 강좌 조회 시스템 구축 | ✅ CLOSED | 검색 API, 더미 데이터 |
| #7 | 예약 시스템 구축 | ✅ CLOSED | 이중 동시성 제어 |
| #9 | 시뮬레이션 티켓팅 시스템 구축 | ✅ CLOSED | 백엔드 완료, 부하 테스트 완료 |
| #12 | 시뮬레이션 프론트엔드 구축 | ✅ CLOSED | React 프론트엔드 완료 |
| #14 | 프론트엔드 연동 백엔드 수정 | ✅ CLOSED | 레이트 리밋, 실시간 카운터 수정 |
| #16 | 슬라이딩 윈도우 기반 유량제어 구현 | ✅ CLOSED | 고정 윈도우 → Sorted Set + Lua 교체 |
| #18 | E2E 통합 테스트 및 failCount 버그 수정 | ✅ CLOSED | Playwright E2E, Redis 직렬화 버그 수정 |
| #20 | RegistrationPage 재시도 버튼 버그 수정 | ✅ CLOSED | retryCount로 useEffect 재실행 |
| #22 | 수강신청 페이지 단계별 필터 UI 재설계 및 데이터 정비 | 🟡 OPEN | UI 완료(PR #24), 데이터 정비 미완 |
| #23 / #25 | 시뮬레이션 버그 수정 (유저 항상 성공 / 봇 대기열 잔류) | ✅ CLOSED | main 직접 커밋(`205c660`) |
| #27 | 수강신청 UX 개선 — 장바구니→결제 플로우, 완료 페이지 개편 | ✅ CLOSED | PR #28 머지 완료 |

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
| #17 | `feature/#16-sliding-window-rate-limit` | 슬라이딩 윈도우 유량제어 | ✅ merged |
| #19 | `feature/#18-e2e-test` | E2E 테스트 및 failCount 버그 수정 | ✅ merged |
| #21 | `feature/#20-registration-ux-fix` | 재시도 버튼 버그 수정 | ✅ merged |
| #24 | `feature/#22-registration-page-redesign` | 수강신청 페이지 재설계 + 정원 설정 | ✅ merged |
| #26 | `feature/#23-simulation-bug-fix` | 시뮬레이션 버그 수정 | ⛔ closed (변경사항 이미 main 포함) |
| #28 | `feature/#27-ux-overhaul` | UX 전면 개편 — 장바구니/결제/결과/배경 | ✅ merged |

> **PR #26 CLOSED 이유**: 브랜치의 두 커밋 중 BotService 수정(`f12b012`)은 main에 동일 커밋(`205c660`)으로 이미 존재했고, RegistrationPage 수정(`25db85d`)은 PR #28에서 더 넓은 범위로 반영되어 충돌 해소 후 변경 내용이 없는 빈 브랜치가 됨.

---

## 구현 완료 기능

### 백엔드

#### 강좌/센터/카테고리
- 강좌 상세 조회, 다중 조건 검색 (센터/카테고리/레벨/교육대상/강사)
- 랜덤 강좌 조회 — 수영 + 성인/청소년 한정 (`findRandomActiveCourse`)
- 센터 목록 / 카테고리 목록 엔드포인트
- 정원 증감 및 상태 자동 전환 (ACTIVE → FULL)

#### 예약 시스템
- **이중 동시성 제어**: Redisson 분산 락 + DB 비관적 락 (SELECT FOR UPDATE)
- 중복 예약 방지, 정원 초과 완벽 차단
- 예약 생성/조회/취소

#### 유량제어
- **Redis Sorted Set 슬라이딩 윈도우**: 요청 시각(ms) score 저장, "지금 기준 과거 60초" 동적 계산
- Lua 스크립트로 원자 처리 (race condition 방지)
- 5회/분 제한, HTTP 429 + `X-RateLimit-Remaining` / `X-RateLimit-Reset` 헤더
- SSE·상태 조회 엔드포인트 제외

#### 대기열 (Redis Sorted Set)
- `enterQueue` / `checkQueueAllowed` / `removeFromQueue` / `clearQueue`
- 상위 10명 이내 진입 허용
- 강좌 ID 동적 스캔

#### 시뮬레이션 엔진
- 멀티스레드: `min(봇 수, 100)` ThreadPool + CountDownLatch
- 봇 전체 스레드 시작 전 동시 대기열 진입 → 공정한 순위 배정
- 재시도 전략: 최대 5회, 2초 간격, 정원 초과·중복 즉시 포기 + 대기열 제거
- stop flag(`AtomicBoolean`)로 안전한 스레드 종료 (`/stop` API)
- 봇별 완료 시 즉시 Redis HINCRBY

#### 유저 직접 예약 (`/reserve`)
- `POST /simulation/{id}/reserve` — 대기열 게이트 우회, 직접 예약
- `UserReserveResponse`: `reserved`, `failReason`, `myPosition`, 통계 반환
- `@Transactional` 제거로 `UnexpectedRollbackException` 방지

#### SSE 실시간 스트림
- `SimulationScheduler` 1초 브로드캐스트
- `successCount`, `failCount`, `queueLength`, `myRank`, `remainingSeats` 전송

### 프론트엔드

#### 페이지
- **StartPage**: 난이도(Easy/Normal/Hard), 총 정원·남은 자리 설정, 수영장 배경 애니메이션
- **RegistrationPage**: 실제 API 강좌 목록, 단계별 필터(센터/카테고리/레벨/대상), 미션 강좌 박스, 장바구니 플로우
- **CartPage**: 예약 강좌 목록, 결제 금액 요약
- **CheckoutPage**: 결제 수단 선택, 약관 동의, 실제 `/reserve` API 호출
- **ResultPage**: `location.state` 기반, 소요 시간(09:00 기준) + 실패 사유 표시

#### 컴포넌트
- **PoolBackground**: 위에서 내려다본 수영장 CSS 애니메이션 (레인 로프, T-라인, 수영 선수 6명 왕복)
- **AccessQueueOverlay**: 새로고침 시 5초 유량제어 팝업, 초기 순번 botCount 기반
- **AquaHeader**: 4단계 진행 표시, 가상 시계, 장바구니 버튼

#### 가상 시계
- 08:59:30 → 09:00:00 (실제 30초)
- `sessionStorage`(`virtualTime`, `virtualStartReal`)로 새로고침 후 시간 연속성 유지

### E2E 테스트
- Playwright 기반 (`frontend/scripts/e2e_test.cjs`, `capture-screenshots.cjs`)
- StartPage → RegistrationPage 이동 및 버튼 상태 검증
- 시뮬레이션 start/status API 검증
- 슬라이딩 윈도우 유량제어 429 확인

---

## 알려진 문제 / 미완성

| # | 항목 | 우선순위 | 설명 |
|---|---|---|---|
| 1 | 이슈 #22 데이터 정비 | 중간 | 강좌 데이터 확충 및 카테고리/센터 정비 미완 |
| 2 | `deleteByCourseId` 최적화 | 낮음 | 현재 findAll + deleteAll → 벌크 쿼리로 개선 가능 |

---

## 다음 작업 우선순위

```
이슈 #22 — 강좌 데이터 정비 (카테고리·센터 데이터 확충)
```

---

## 로컬 실행 체크리스트

- [ ] Docker Desktop 실행
- [ ] `docker-compose up -d` (Redis + Redis Commander)
- [ ] MySQL `aquarush` 데이터베이스 생성
- [ ] `cd backend/ticketing && ./gradlew bootRun`
- [ ] `cd frontend && npm run dev`
- [ ] http://localhost:5175 접속
