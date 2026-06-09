import { chromium } from '@playwright/test'
import path from 'path'
import fs from 'fs'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = 'http://localhost:5179'
const OUT_DIR = path.join(__dirname, '..', 'docs', 'screenshots')

fs.mkdirSync(OUT_DIR, { recursive: true })

// 9:00 이후 virtualTime
const PAST_NINE = new Date('2025-01-15T09:01:00').getTime().toString()
const PAST_NINE_REAL = (Date.now() - 5000).toString()

const BASE_CONFIG = JSON.stringify({ nickname: '테스트유저', botCount: 100, courseId: 1 })
const BASE_META   = JSON.stringify({ name: '성인 자유수영', capacity: 20 })
const FAKE_SIM_ID = 'preview-sim-123'

// Result mock data: success
const RESULT_SUCCESS = JSON.stringify({
  status: 'COMPLETED',
  courseName: '성인 자유수영',
  totalParticipants: 101,
  successCount: 20,
  failCount: 81,
  remainingSeats: 0,
  queueLength: 0,
  myReservationSuccess: true,
  myPosition: 7,
})

// Result mock data: failure
const RESULT_FAIL = JSON.stringify({
  status: 'COMPLETED',
  courseName: '성인 자유수영',
  totalParticipants: 101,
  successCount: 20,
  failCount: 81,
  remainingSeats: 0,
  queueLength: 0,
  myReservationSuccess: false,
  myPosition: null,
})

async function shot(page, name) {
  await page.waitForTimeout(600)
  await page.screenshot({ path: path.join(OUT_DIR, `${name}.png`), fullPage: true })
  console.log(`✓ ${name}.png`)
}

async function setSession(page, entries) {
  await page.evaluate((kv) => {
    Object.entries(kv).forEach(([k, v]) => sessionStorage.setItem(k, v))
  }, entries)
}

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } })
  const page = await ctx.newPage()

  /* ── 1. StartPage ── */
  await page.goto(BASE_URL)
  await shot(page, '01_start_page')

  /* ── 2. StartPage — error state ── */
  await page.click('button.start-btn')
  await page.waitForTimeout(300)
  await shot(page, '02_start_page_error')

  /* ── 3. RegistrationPage — countdown (08:59:30 시작) ── */
  await page.goto(BASE_URL)
  await setSession(page, { aquarush_config: BASE_CONFIG })
  await page.goto(`${BASE_URL}/registration`)
  await page.waitForTimeout(800)
  await shot(page, '03_registration_countdown')

  /* ── 4. RegistrationPage — AccessQueueOverlay (9시 이후 새로고침) ── */
  await page.goto(BASE_URL)
  await setSession(page, {
    aquarush_config:     BASE_CONFIG,
    virtualTime:         PAST_NINE,
    virtualStartReal:    PAST_NINE_REAL,
    aquarush_simId:      FAKE_SIM_ID,
    aquarush_meta:       BASE_META,
  })
  await page.goto(`${BASE_URL}/registration`)
  await page.waitForTimeout(800)
  await shot(page, '04_access_queue_overlay')

  /* ── 5. RegistrationPage — 버튼 활성화 (AccessQueue 완료 후) ── */
  // AccessQueueOverlay는 5초 후 자동 완료됨 — 5.5초 대기
  await page.waitForTimeout(5500)
  await shot(page, '05_registration_active')

  /* ── 6. QueueModal (waiting state — mock) ── */
  // mission 버튼 클릭 시 백엔드 없이 에러가 나므로, QueueModal을 직접 inject
  await page.evaluate(() => {
    // React 컴포넌트 상태를 직접 바꿀 수 없어 accessGranted=true 상태에서 버튼 클릭 시뮬
    // mock: overlay div 삽입으로 스크린샷
    const overlay = document.createElement('div')
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;z-index:500'
    overlay.innerHTML = `
      <div style="background:white;border-radius:20px;padding:2rem;width:420px;box-shadow:0 20px 60px rgba(0,0,0,.25);">
        <div style="text-align:center;margin-bottom:1.5rem">
          <div style="font-size:3.5rem">⏳</div>
          <h2 style="font-size:1.5rem;font-weight:700;color:#333;margin:8px 0 4px">대기 중입니다</h2>
          <p style="font-size:.9rem;color:#1e90ff;font-weight:600">성인 자유수영 · 센터 A</p>
        </div>
        <div style="background:linear-gradient(135deg,#e3f2fd,#bbdefb);border:2px solid #1e90ff;border-radius:14px;padding:1.2rem;text-align:center;margin-bottom:1.2rem">
          <div style="font-size:.85rem;color:#0066cc;font-weight:600;margin-bottom:4px">현재 내 순번</div>
          <div style="font-size:3rem;font-weight:800;color:#1e90ff;line-height:1;margin-bottom:6px">47<span style="font-size:1.3rem">번째</span></div>
          <div style="font-size:.9rem;color:#555">내 앞에 <strong style="color:#dc3545">46명</strong> 대기 중</div>
        </div>
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:1.2rem">
          ${[['전체 참가자','101명','#333'],['대기 중','52명','#ff9800'],['예약 완료','12명','#28a745'],['남은 좌석','8석','#1e90ff']].map(([l,v,c]) =>
            `<div style="background:#f8f9fa;border-radius:8px;padding:8px 4px;text-align:center">
              <span style="display:block;font-size:10px;color:#888;font-weight:600;margin-bottom:3px">${l}</span>
              <span style="display:block;font-size:1rem;font-weight:700;color:${c}">${v}</span>
            </div>`
          ).join('')}
        </div>
        <div style="background:#e3f2fd;border-radius:6px;height:10px;overflow:hidden;margin-bottom:1.2rem">
          <div style="height:100%;width:48%;background:linear-gradient(90deg,#1e90ff,#0066cc);border-radius:6px"></div>
        </div>
        <div style="text-align:center;font-size:.82rem;color:#888;padding-top:.5rem;border-top:1px solid #f0f0f0">🔄 실시간으로 업데이트 중입니다</div>
      </div>`
    document.body.appendChild(overlay)
    window.__mockOverlay = overlay
  })
  await shot(page, '06_queue_modal_waiting')
  await page.evaluate(() => window.__mockOverlay?.remove())

  /* ── 7. QueueModal — success result ── */
  await page.evaluate(() => {
    const overlay = document.createElement('div')
    overlay.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;z-index:500'
    overlay.innerHTML = `
      <div style="background:white;border-radius:20px;padding:2rem;width:420px;box-shadow:0 20px 60px rgba(0,0,0,.25);">
        <div style="text-align:center;margin-bottom:1.5rem">
          <div style="font-size:3.5rem">🎉</div>
          <h2 style="font-size:1.5rem;font-weight:700;color:#333;margin:8px 0 4px">예약 성공!</h2>
          <p style="font-size:.9rem;color:#1e90ff;font-weight:600">7번째로 예약이 확정되었습니다!</p>
        </div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:1.5rem;padding:1rem;border-radius:10px;background:linear-gradient(135deg,#e8f5e9,#c8e6c9)">
          <div style="background:#f8f9fa;border-radius:8px;padding:8px 4px;text-align:center">
            <span style="display:block;font-size:10px;color:#888;font-weight:600;margin-bottom:3px">예약 성공</span>
            <span style="display:block;font-size:1rem;font-weight:700;color:#28a745">20명</span>
          </div>
          <div style="background:#f8f9fa;border-radius:8px;padding:8px 4px;text-align:center">
            <span style="display:block;font-size:10px;color:#888;font-weight:600;margin-bottom:3px">예약 실패</span>
            <span style="display:block;font-size:1rem;font-weight:700;color:#dc3545">81명</span>
          </div>
        </div>
        <button style="width:100%;background:linear-gradient(135deg,#1e90ff,#0066cc);color:white;border:none;padding:.9rem;border-radius:50px;font-size:1rem;font-weight:700;cursor:pointer">다음으로 →</button>
      </div>`
    document.body.appendChild(overlay)
    window.__mockOverlay = overlay
  })
  await shot(page, '07_queue_modal_success')
  await page.evaluate(() => window.__mockOverlay?.remove())

  /* ── 8. ResultPage — success (React Router state inject) ── */
  await page.goto(BASE_URL)
  await page.evaluate((stateJson) => {
    const st = JSON.parse(stateJson)
    const histState = { usr: { status: st } }
    window.history.replaceState(histState, '', '/result/preview-sim-123')
  }, RESULT_SUCCESS)
  await page.reload()
  await page.waitForTimeout(1000)
  await shot(page, '08_result_success')

  /* ── 9. ResultPage — failure ── */
  await page.goto(BASE_URL)
  await page.evaluate((stateJson) => {
    const st = JSON.parse(stateJson)
    const histState = { usr: { status: st } }
    window.history.replaceState(histState, '', '/result/preview-sim-123')
  }, RESULT_FAIL)
  await page.reload()
  await page.waitForTimeout(1000)
  await shot(page, '09_result_fail')

  /* ── 10. CartPage ── */
  await page.goto(BASE_URL)
  await page.evaluate(() => {
    const cartItems = [
      { id: 1, center: '센터 A', category: '수영', name: '성인 자유수영', time: '월,수,금 14:00-15:00', target: '성인', enrolled: 0, capacity: 20, price: 80000 }
    ]
    const histState = {
      usr: {
        nickname: '테스트유저', botCount: 100, courseId: 1,
        simulationId: 'preview-sim-123',
        cart: cartItems,
        finalStatus: { myReservationSuccess: true, myPosition: 7 }
      }
    }
    window.history.replaceState(histState, '', '/cart/preview-sim-123')
  })
  await page.reload()
  await page.waitForTimeout(800)
  await shot(page, '10_cart_page')

  /* ── 11. CheckoutPage ── */
  await page.goto(BASE_URL)
  await page.evaluate(() => {
    const cartItems = [
      { id: 1, center: '센터 A', category: '수영', name: '성인 자유수영', time: '월,수,금 14:00-15:00', target: '성인', enrolled: 0, capacity: 20, price: 80000 }
    ]
    const histState = {
      usr: {
        nickname: '테스트유저', botCount: 100, courseId: 1,
        simulationId: 'preview-sim-123',
        cart: cartItems,
      }
    }
    window.history.replaceState(histState, '', '/checkout/preview-sim-123')
  })
  await page.reload()
  await page.waitForTimeout(800)
  await shot(page, '11_checkout_page')

  await browser.close()
  console.log(`\n✅ 모든 스크린샷 저장 완료: ${OUT_DIR}`)
})()
