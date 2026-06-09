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
