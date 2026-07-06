import './AccessQueueOverlay.css'

export default function AccessQueueOverlay({
  position = 0,
  initialPosition = 1,
  estimatedWaitSeconds = 0,
  botsInQueue = 0,
  isOpen = false,
  secondsUntilOpen = 0,
}) {
  const progress = initialPosition > 0
    ? Math.round(((initialPosition - position) / initialPosition) * 100)
    : 0

  // 내 뒤에 대기 중인 인원 = 전체 큐 인원 - 내 순번
  const behind = Math.max(0, botsInQueue + 1 - position)

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
            내 뒤에 <strong>{behind.toLocaleString()}명</strong> 대기 중
          </div>
        </div>

        {isOpen ? (
          <>
            <div className="aq-progress-wrap">
              <div className="aq-progress-header">
                <span>대기열 소진률</span>
                <span>{progress}%</span>
              </div>
              <div className="aq-progress-track">
                <div className="aq-progress-fill" style={{ width: `${progress}%` }} />
              </div>
            </div>
            <p className="aq-notice">잠시만 기다려주세요 — 약 {estimatedWaitSeconds}초 후 입장됩니다</p>
          </>
        ) : (
          <div className="aq-countdown-box">
            <div className="aq-countdown-label">수강신청 오픈까지</div>
            <div className="aq-countdown-num">{secondsUntilOpen}<span>초</span></div>
            <p className="aq-notice">09:00 오픈 후 순서대로 입장 처리됩니다</p>
          </div>
        )}
      </div>
    </div>
  )
}
