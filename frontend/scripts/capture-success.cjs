// botCount=1, remainingSeats=10 → 유저가 성공할 확률이 높음
const { chromium } = require('playwright')

const OPEN_TIME = new Date('2025-01-15T09:00:00').getTime()
const config = { nickname: 'user_win', botCount: 1, courseId: 1, totalSeats: 20, remainingSeats: 10 }

;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  await page.setViewportSize({ width: 1280, height: 800 })

  await page.goto('http://localhost:5175')
  await page.evaluate(({ o, cfg }) => {
    sessionStorage.clear()
    sessionStorage.setItem('virtualTime', String(o))
    sessionStorage.setItem('virtualStartReal', String(Date.now()))
    sessionStorage.setItem('aquarush_config', JSON.stringify(cfg))
  }, { o: OPEN_TIME, cfg: config })
  await page.goto('http://localhost:5175/registration')

  // 오버레이 통과 + 시뮬레이션 시작
  await page.waitForTimeout(7000)

  // 장바구니 버튼 클릭
  await page.waitForSelector('button.add-cart-btn:not([disabled])', { timeout: 8000 })
  await page.click('button.add-cart-btn:not([disabled])')
  await page.waitForTimeout(400)

  // 장바구니 이동
  await page.click('.cart-btn')
  await page.waitForTimeout(800)

  // 결제 페이지
  await page.click('.checkout-btn')
  await page.waitForTimeout(600)

  // 전체 동의
  const allCheck = await page.$('input[type="checkbox"]:first-of-type')
  if (allCheck) await allCheck.check()
  await page.waitForTimeout(300)

  // 결제하기
  await page.click('button.payment-btn:not([disabled])')
  await page.waitForTimeout(3000)

  if (page.url().includes('/result')) {
    await page.screenshot({ path: 'docs/screenshots/08_result_success.png' })
    console.log('success result captured:', page.url())
  } else {
    console.log('not on result page:', page.url())
  }

  await browser.close()
})().catch(e => { console.error(e.message); process.exit(1) })
