import './AccessQueueOverlay.css'

export default function AccessQueueOverlay({
  position = 0,
  initialPosition = 1,
  estimatedWaitSeconds = 0,
  totalBots = 0,
  botsInQueue = 0,
  botsAdmitted = 0,
}) {
  const progress = initialPosition > 0
    ? Math.round(((initialPosition - position) / initialPosition) * 100)
    : 0

  const botAdmitProgress = totalBots > 0
    ? Math.round((botsAdmitted / totalBots) * 100)
    : 0

  return (
    <div className="aq-overlay">
      <div className="aq-box">
        <div className="aq-icon">⏳</div>
        <h2 className="aq-title">접속 대기 중입니다</h2>
        <p className="aq-desc">수강신청 시작으로 많은 분이 동시에 접속 중입니다</p>

        <div className="aq-rank-box">
          <div className="aq-rank-label">현재 내 순번</div>
          <div className="aq-rank-num">
            {position.toLocaleString()}<span>번째</span>
          </div>
          <div className="aq-rank-sub">
            내 앞에 <strong>{Math.max(0, position - 1).toLocaleString()}명</strong> 대기 중
          </div>
        </div>

        <div className="aq-progress-wrap">
          <div className="aq-progress-header">
            <span>대기열 소진률</span>
            <span>{progress}%</span>
          </div>
          <div className="aq-progress-track">
            <div className="aq-progress-fill" style={{ width: `${progress}%` }} />
          </div>
        </div>

        {totalBots > 0 && (
          <div className="aq-bot-stats">
            <div className="aq-bot-title">실시간 접속 현황</div>
            <div className="aq-bot-row">
              <span className="aq-bot-label">대기 중</span>
              <span className="aq-bot-val waiting">{botsInQueue.toLocaleString()}명</span>
            </div>
            <div className="aq-bot-row">
              <span className="aq-bot-label">입장 완료</span>
              <span className="aq-bot-val admitted">{botsAdmitted.toLocaleString()}명</span>
            </div>
            <div className="aq-bot-progress-wrap">
              <div className="aq-bot-progress-header">
                <span>입장률</span>
                <span>{botAdmitProgress}%</span>
              </div>
              <div className="aq-progress-track">
                <div className="aq-bot-progress-fill" style={{ width: `${botAdmitProgress}%` }} />
              </div>
            </div>
          </div>
        )}

        <p className="aq-notice">잠시만 기다려주세요 — 약 {estimatedWaitSeconds}초 후 입장됩니다</p>
      </div>
    </div>
  )
}
