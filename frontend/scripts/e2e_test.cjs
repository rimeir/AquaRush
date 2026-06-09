const { chromium } = require('playwright');
const { execSync } = require('child_process');

const curl = (args) => {
  try {
    return execSync('curl -s ' + args, { encoding: 'utf8' });
  } catch (e) {
    return e.stdout || e.message;
  }
};

(async () => {
  const browser = await chromium.launch({ headless: false, slowMo: 300 });
  const page = await browser.newPage();
  const log = (msg) => console.log(msg);

  try {
    // 1. StartPage
    await page.goto('http://localhost:5173');
    await page.waitForLoadState('networkidle');
    log('✅ StartPage 로드 완료');
    await page.screenshot({ path: 'docs/screenshots/e2e_01_start.png', fullPage: true });

    await page.locator('input[type="text"]').fill('테스트유저');
    log('✅ 닉네임 입력 완료');

    await page.locator('button.start-btn').click();
    log('✅ 티켓팅 시작하기 버튼 클릭');

    await page.waitForURL('**/registration', { timeout: 5000 }).catch(() => {});
    const url = page.url();
    log('📍 URL: ' + url);

    // 2. RegistrationPage
    if (url.includes('registration')) {
      log('✅ RegistrationPage 진입 성공');
      await page.waitForTimeout(1500);
      await page.screenshot({ path: 'docs/screenshots/e2e_02_registration.png', fullPage: true });

      const buttons = page.locator('button');
      const count = await buttons.count();
      log('버튼 수: ' + count);
      for (let i = 0; i < count; i++) {
        const text = await buttons.nth(i).textContent();
        const disabled = await buttons.nth(i).isDisabled();
        log('  [' + text.trim() + '] disabled=' + disabled);
      }

      const bodyText = await page.textContent('body');
      if (bodyText.includes('08:5') || bodyText.includes('09:00')) {
        log('✅ 가상 시계 렌더링 확인');
      }
    } else {
      log('❌ RegistrationPage 미진입 - URL: ' + url);
    }

    // 3. 시뮬레이션 API 테스트 (curl)
    log('\n--- 시뮬레이션 API 테스트 ---');
    const startRaw = curl('-X POST http://localhost:8080/api/v1/simulation/start -H "Content-Type: application/json" -d "{\\"courseId\\":1,\\"botCount\\":5,\\"nickname\\":\\"e2e_test\\"}"');
    const startBody = JSON.parse(startRaw);
    log('POST /simulation/start → success=' + startBody.success);

    if (startBody.success) {
      const simId = startBody.data.simulationId;
      log('✅ simulationId: ' + simId);
      log('  totalSeats=' + startBody.data.totalSeats);
      log('  status=' + startBody.data.status);

      await page.waitForTimeout(4000);

      const statusRaw = curl('http://localhost:8080/api/v1/simulation/status/' + simId);
      const statusBody = JSON.parse(statusRaw);
      const d = statusBody.data;
      log('\nGET /simulation/status →');
      log('  status=' + d.status);
      log('  successCount=' + d.successCount + ', failCount=' + d.failCount);
      log('  remainingSeats=' + d.remainingSeats + ', totalSeats=' + d.totalSeats);
      log('  myReservationSuccess=' + d.myReservationSuccess + ', myPosition=' + d.myPosition);
      log('  myRank=' + d.myRank + ', queueLength=' + d.queueLength);
    } else {
      log('❌ 시뮬레이션 시작 실패: ' + JSON.stringify(startBody));
    }

    // 4. 슬라이딩 윈도우 유량제어 테스트
    log('\n--- 슬라이딩 윈도우 유량제어 테스트 (6회 연속) ---');
    for (let i = 1; i <= 6; i++) {
      const raw = execSync(
        'curl -s -o /dev/null -w "%{http_code} Remaining=%header{X-RateLimit-Remaining} Reset=%header{X-RateLimit-Reset}s" ' +
        '-H "X-Session-Id: ratelimit-e2e-session" http://localhost:8080/api/v1/courses/1',
        { encoding: 'utf8' }
      );
      log('요청 ' + i + ': ' + raw.trim());
    }

  } catch (e) {
    log('❌ 오류: ' + e.message);
    console.error(e);
  } finally {
    await browser.close();
  }
})();
