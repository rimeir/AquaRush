const { chromium } = require('playwright')

const OPEN_TIME = new Date('2025-01-15T09:00:00').getTime()
const START_TIME = new Date('2025-01-15T08:59:30').getTime()
const config = { nickname: 'user_1234', botCount: 100, courseId: 1, totalSeats: 20, remainingSeats: 5 }

;(async () => {
  const browser = await chromium.launch({ headless: true })

  // 03. 카운트다운 (9시 이전)
  {
    const page = await browser.newPage()
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto('http://localhost:5175')
    await page.evaluate(({ s, cfg }) => {
      sessionStorage.clear()
      sessionStorage.setItem('virtualTime', String(s))
      sessionStorage.setItem('virtualStartReal', String(Date.now()))
      sessionStorage.setItem('aquarush_config', JSON.stringify(cfg))
    }, { s: START_TIME, cfg: config })
    await page.goto('http://localhost:5175/registration')
    await page.waitForTimeout(1500)
    await page.screenshot({ path: 'docs/screenshots/03_registration_countdown.png' })
    console.log('03 done')
    await page.close()
  }

  // 04. 접속 유량제어 오버레이
  {
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
    await page.waitForTimeout(700)
    await page.screenshot({ path: 'docs/screenshots/04_access_queue_overlay.png' })
    console.log('04 done')
    await page.close()
  }

  // 05 ~ 08. 전체 플로우
  {
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

    // 오버레이 5초 + 시뮬레이션 시작 대기
    await page.waitForTimeout(6800)
    await page.screenshot({ path: 'docs/screenshots/05_registration_active.png' })
    console.log('05 done')

    // 미션 강좌 장바구니 버튼 클릭
    try {
      await page.waitForSelector('button.add-cart-btn:not([disabled])', { timeout: 8000 })
      await page.click('button.add-cart-btn:not([disabled])')
      await page.waitForTimeout(400)
      console.log('clicked cart btn')
    } catch (e) {
      console.log('cart btn not found:', e.message)
    }

    // 헤더 장바구니 버튼 클릭
    try {
      await page.click('.cart-btn')
      await page.waitForTimeout(1000)
    } catch (e) {
      console.log('header cart btn not found:', e.message)
    }

    if (page.url().includes('/cart')) {
      await page.screenshot({ path: 'docs/screenshots/10_cart_page.png' })
      console.log('10 done')

      // 결제 페이지 이동
      try {
        await page.click('.checkout-btn')
        await page.waitForTimeout(800)
      } catch (e) {
        console.log('checkout btn not found:', e.message)
      }
    }

    if (page.url().includes('/checkout')) {
      await page.screenshot({ path: 'docs/screenshots/11_checkout_page.png' })
      console.log('11 done')

      // 전체 동의 체크
      try {
        const allCheck = await page.$('input[type="checkbox"]:first-of-type')
        if (allCheck) await allCheck.check()
        await page.waitForTimeout(300)
      } catch (e) { console.log('agree check failed:', e.message) }

      // 결제하기 클릭
      try {
        await page.click('button.payment-btn:not([disabled])')
        await page.waitForTimeout(3000)
      } catch (e) { console.log('pay btn not found:', e.message) }
    }

    if (page.url().includes('/result')) {
      await page.screenshot({ path: 'docs/screenshots/08_result_success.png' })
      console.log('08 done')
    }

    await page.close()
  }

  await browser.close()
  console.log('all done')
})().catch(e => { console.error(e.message); process.exit(1) })
