# Frontend Changes — AquaRush 수켓팅 시뮬레이터

> 이슈: #12 · 브랜치: `feature/#12-simulation-frontend`

---

## 프로젝트 초기 설정

- React 19 + Vite 8 기반 SPA 프로젝트 구성
- React Router v7 라우팅 설정 (`/`, `/registration`, `/cart/:id`, `/checkout/:id`, `/result/:id`)
- Axios 기반 API 클라이언트 (`/api/v1` 프록시)
- 기존 정적 HTML 파일(cart.html, checkout.html, index.html, registration.html, result.html) 제거

---

## 페이지별 변경 사항

### StartPage (`/`)
- 닉네임 입력 및 난이도(봇 수) 설정 UI
- 프리셋: Easy(100명), Normal(500명), Hard(1000명)
- 슬라이더로 10~1000명 직접 설정 가능
- 시작 시 이전 세션 데이터 초기화 후 `sessionStorage`에 설정 저장

### RegistrationPage (`/registration`)
- **9시 자동 봇 시작**: `useVirtualClock`의 `isOpen` 전환 시 `startSimulation` API 자동 호출 (버튼 클릭 불필요)
- **`openOnMount` 패턴**: 컴포넌트 마운트 시점이 이미 9시 이후인지 캡처 → 새로고침 vs. 동일 세션 구분
- **`autoStartedRef`**: `startSimulation` 중복 호출 방지 (봇 수 배증 버그 수정)
- **상태별 UI**:
  - 9시 이전: 카운트다운 배너 + 버튼 잠금
  - 9시 이후 (같은 세션): "F5 새로고침" 안내 배너 + 버튼 비활성화 유지
  - 9시 이후 새로고침: `AccessQueueOverlay` 표시 → 완료 시 버튼 활성화
- **실패 시 ResultPage 이동** (이전: CartPage)
- `sessionStorage` 키: `aquarush_config`, `aquarush_simId`, `aquarush_meta`, `virtualTime`, `virtualStartReal`

### AccessQueueOverlay (신규 컴포넌트)
- 9시 이후 새로고침 시 표시되는 페이지 레벨 유량제어 팝업
- 500~2500 범위 랜덤 순번에서 0까지 5초간 카운트다운
- 진행률 바 + 실시간 순번 업데이트 (80ms 인터벌)
- 완료 후 `onComplete()` 콜백 호출 → `accessGranted = true`
- **예약 QueueModal과 별개** (접속 제어 vs. 예약 대기)

### QueueModal (업데이트)
- **SSE + 1.5초 폴링 이중 업데이트**: SSE 연결 전 봇 완료(경쟁 조건) 대비
- 마운트 시 즉시 `getStatus()` 호출 → 이미 완료된 시뮬레이션 즉시 표시
- 실시간 표시 항목: 예약 완료 수 (`successCount`), 남은 좌석 (`remainingSeats`), 대기 중 (`queueLength`), 내 순번 (`myRank`)
- 완료 시 성공/실패 결과 화면 + "다음으로" 버튼

### ResultPage (`/result/:simulationId`)
- 성공/실패에 따른 차별화 UI (🎉 / 😢)
- 결과 내역: 강좌명, 총 참가자, 예약 성공/실패 수, 성공률, 내 순위
- "다시 도전하기" 클릭 시 모든 세션 데이터 초기화 후 `/` 이동

### CartPage (`/cart/:simulationId`)
- 예약 성공 강좌 목록 + 결제 정보 요약
- 결제하기 → CheckoutPage, 강좌 더 담기 → RegistrationPage

### CheckoutPage (`/checkout/:simulationId`)
- 결제 수단 선택 (신용카드, 계좌이체, 휴대폰)
- 약관 동의 체크박스
- 결제 완료 처리

---

## 공통 컴포넌트

| 컴포넌트 | 설명 |
|---|---|
| `AquaHeader` | 진행 단계 표시 (강좌선택 → 장바구니 → 결제하기 → 완료), 가상 시계, 장바구니 버튼 |
| `StatCard` | 시뮬레이션 통계 카드 (성공/실패/대기 등) |
| `QueueBar` | 대기열 시각화 바 |

---

## API / 훅

### `api/simulation.js`
| 함수 | 설명 |
|---|---|
| `startSimulation(courseId, botCount, nickname)` | 시뮬레이션 시작 |
| `getStatus(simulationId)` | 현재 상태 조회 |
| `stopSimulation(simulationId)` | 시뮬레이션 중단 |
| `createSseConnection(simId, onMsg, onComplete, onError)` | SSE 실시간 연결 |

### `hooks/useVirtualClock.js`
- 08:59:30 → 09:00:00 가상 시계 (30초 = 실제 30초)
- `sessionStorage` 저장/복원으로 새로고침 후에도 시간 연속성 유지
- 반환: `{ time, isOpen, secondsUntilOpen }`

---

## 백엔드 수정 (프론트엔드 연동)

| 파일 | 변경 내용 |
|---|---|
| `WebConfig.java` | `/api/v1/simulation/status/**`, `/api/v1/simulation/live/**` 레이트 리밋 제외 |
| `SimulationStatusResponse.java` | `totalSeats` 필드 추가 |
| `SimulationService.java` | `getStatus()` 응답에 `totalSeats` 값 포함 |
| `BotService.java` | 봇별 완료 시 Redis HINCRBY로 `successCount`/`failCount` 실시간 증가 |

---

## UI 스크린샷

| 화면 | 파일 |
|---|---|
| 시작 페이지 | `docs/screenshots/01_start_page.png` |
| 시작 페이지 (유효성 오류) | `docs/screenshots/02_start_page_error.png` |
| 수강신청 (카운트다운) | `docs/screenshots/03_registration_countdown.png` |
| 접속 유량제어 오버레이 | `docs/screenshots/04_access_queue_overlay.png` |
| 수강신청 (버튼 활성화) | `docs/screenshots/05_registration_active.png` |
| 예약 대기열 모달 | `docs/screenshots/06_queue_modal_waiting.png` |
| 예약 성공 모달 | `docs/screenshots/07_queue_modal_success.png` |
| 결과 페이지 (성공) | `docs/screenshots/08_result_success.png` |
| 결과 페이지 (실패) | `docs/screenshots/09_result_fail.png` |
| 장바구니 | `docs/screenshots/10_cart_page.png` |
| 결제 확인 | `docs/screenshots/11_checkout_page.png` |

---

---

## 수강신청 페이지 전면 재설계 (이슈 #22, PR #24)

> 브랜치: `feature/#22-registration-page-redesign`

### StartPage

- 총 정원 / 남은 자리 직접 설정 UI 추가 (±버튼 + 입력)
- 시뮬레이션 경쟁 조건을 유저가 직접 구성

### RegistrationPage 전면 재작성

**실제 API 연동**

더미 데이터 제거. 센터·카테고리·강좌 목록을 백엔드 API에서 가져옴.

```
GET /api/v1/centers
GET /api/v1/categories
GET /api/v1/courses/search?centerId=&categoryId=&level=&targetAudience=
GET /api/v1/courses/{id}        — 미션 강좌 메타 로드
GET /api/v1/courses/random      — 시작 시 미션 강좌 배정
```

**단계별 필터 UI**

| 필터 | 타입 | 선택지 |
|---|---|---|
| 체육센터 | 탭 | 전체 + 백엔드 센터 목록 |
| 대분류 | 탭 | 전체 + 백엔드 카테고리 목록 |
| 소분류 | 칩 | 전체 / 초급 / 중급 / 고급 |
| 교육 대상 | 칩 | 전체 / 성인·청소년 / 어린이 |

**미션 강좌 박스**

- 시뮬레이션 시작 응답(`SimulationStatusResponse`)에서 강좌 메타 수신
- 강좌명·센터·요일·시간·레벨·대상·정원 표시
- 강좌 목록 테이블에서 미션 강좌 행 하이라이트

### `api/simulation.js` 추가 함수

| 함수 | 설명 |
|---|---|
| `getRandomCourse()` | 시작 시 미션 강좌 랜덤 배정 |
| `getCenters()` | 센터 목록 조회 |
| `getCategories()` | 카테고리 목록 조회 |
| `getCourses(params)` | 강좌 검색 (필터 파라미터) |
| `getCourseDetail(id)` | 강좌 상세 (가격 포함) |

---

---

## AccessQueueOverlay 버그 수정 (이슈 #23)

### 문제 1 — 초기 순번 하드코딩

기존: 500~2500 고정 범위 → 봇 수와 무관하게 동일한 순번 표시.

```jsx
// 변경 전
const initialPos = useRef(Math.floor(Math.random() * 2000) + 500)

// 변경 후 — botCount 기반으로 ±20% 범위
const initialPos = useRef(botCount + Math.floor(Math.random() * Math.ceil(botCount * 0.2)))
```

### 문제 2 — 시뮬레이션 시작 전 오버레이 미표시

9시 이후 새로고침 시 시뮬레이션 ID가 아직 없으면 오버레이가 뜨지 않던 문제.

```jsx
// 변경 전
const showAccessQueue = openOnMount && !!currentSimId && !accessGranted

// 변경 후 — currentSimId 조건 제거
const showAccessQueue = openOnMount && !accessGranted
```

---

---

## UX 전면 개편 — 장바구니 플로우 (이슈 #27, PR #28)

> 브랜치: `feature/#27-ux-overhaul`

### 변경 배경

기존에는 수강신청 버튼 클릭 시 QueueModal(대기열 팝업)이 열리고 봇과 함께 자동 예약 처리되었습니다. 실제 수강신청 사이트처럼 **강좌 탐색 → 장바구니 담기 → 결제 → 결과** 플로우로 전환했습니다.

### RegistrationPage

- `QueueModal` 완전 제거
- 모든 강좌(미션 포함) "장바구니" 버튼으로 통일
- `normalizeCartItem()`: API 응답 필드명(`courseName`, `centerName`, `timeSlot`)을 카트 필드명(`name`, `center`, `time`)으로 정규화
- 미션 강좌 정원 셀 항상 초록색 (시뮬레이션 초기화로 `isFull=true`가 되는 문제 해결)
- 새로고침 안내 배너 복원 (`isOpen && !openOnMount && !accessGranted` 조건)
- 가격 보존: `startSimulation` 응답 수신 시 `getCourseDetail`에서 받아온 `price` 유지

### CheckoutPage

- 실제 `POST /simulation/{id}/reserve` 호출
- 미션 강좌가 없는 경우 즉시 실패 결과로 이동 (사유: "미션 강좌가 아닌 다른 강좌는 수강신청할 수 없습니다.")
- `getElapsedSeconds()`: `virtualStartReal + 30000` 기준 소요 시간 계산

### ResultPage 완전 재작성

SSE 폴링 제거. `location.state`에서 직접 데이터 수신.

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| 데이터 소스 | SSE + API 폴링 | `location.state` (CheckoutPage에서 전달) |
| 성공 지표 | 성공률(%) | 소요 시간 (09:00 기준 초) |
| 실패 표시 | 없음 | `failReason` 문자열 표시 |

```jsx
// state 구조
{
  reserved: boolean,
  failReason: string | null,
  courseName: string,
  myPosition: number | null,
  totalParticipants: number,
  successCount: number,
  failCount: number,
  elapsedSeconds: number,
}
```

### StartPage + PoolBackground (신규 컴포넌트)

위에서 내려다본 수영장 배경 애니메이션. `<PoolBackground />`는 `.start-page` 외부에 렌더링 (z-index 분리).

| 요소 | 구현 방식 |
|---|---|
| 수영장 물 | 하늘색 그라데이션 + 대각선 셔머 애니메이션 |
| 코스틱 효과 | 10개 블러 타원이 각각 다른 속도로 부유 |
| 레인 로프 | 7줄, 파랑/노랑 부표 `justify-content: space-around` |
| T-라인 | 각 레인 중앙에 파란 선 + 양 끝 가로막대 (`::before`/`::after`) |
| 수영 선수 | 6명, 다른 duration/delay로 `scaleX(-1)` 방향 전환 왕복 |

카드: `backdrop-filter: blur(12px)` frosted glass 효과 적용.

### `api/simulation.js`

`reserveForUser(simulationId)` 추가.

---

## UI 스크린샷 (최신)

| 화면 | 파일 |
|---|---|
| 시작 페이지 (수영장 배경) | `docs/screenshots/01_start_page.png` |
| 수강신청 (카운트다운) | `docs/screenshots/03_registration_countdown.png` |
| 접속 유량제어 오버레이 | `docs/screenshots/04_access_queue_overlay.png` |
| 수강신청 (시뮬레이션 진행 중) | `docs/screenshots/05_registration_active.png` |
| 장바구니 | `docs/screenshots/10_cart_page.png` |
| 결제 | `docs/screenshots/11_checkout_page.png` |
| 결과 (성공) | `docs/screenshots/08_result_success.png` |
| 결과 (실패) | `docs/screenshots/09_result_fail.png` |
