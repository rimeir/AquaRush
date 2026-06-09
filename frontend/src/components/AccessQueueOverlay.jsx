import { useState, useEffect, useRef } from 'react'
import './AccessQueueOverlay.css'

const DURATION = 5000

export default function AccessQueueOverlay({ onComplete }) {
  const initialPos = useRef(Math.floor(Math.random() * 2500) + 500)
  const [position, setPosition] = useState(initialPos.current)
  const [progress, setProgress] = useState(0)
  const startRef = useRef(Date.now())

  useEffect(() => {
    const id = setInterval(() => {
      const elapsed = Date.now() - startRef.current
      if (elapsed >= DURATION) {
        clearInterval(id)
        setPosition(0)
        setProgress(100)
        setTimeout(onComplete, 300)
        return
      }
      const ratio = elapsed / DURATION
      setPosition(Math.ceil(initialPos.current * (1 - ratio)))
      setProgress(Math.round(ratio * 100))
    }, 80)
    return () => clearInterval(id)
  }, [onComplete])

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

        <p className="aq-notice">잠시만 기다려주세요 — 곧 입장됩니다</p>
      </div>
    </div>
  )
}
