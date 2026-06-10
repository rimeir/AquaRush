import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getRandomCourse } from '../api/simulation'
import './StartPage.css'

const PRESETS = [
  { label: 'Easy',   botCount: 100,  description: '100명 경쟁' },
  { label: 'Normal', botCount: 500,  description: '500명 경쟁' },
  { label: 'Hard',   botCount: 1000, description: '1000명 경쟁' },
]

export default function StartPage() {
  const navigate = useNavigate()
  const [botCount, setBotCount] = useState(100)
  const [totalSeats, setTotalSeats] = useState(20)
  const [remainingSeats, setRemainingSeats] = useState(5)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const filledSeats = totalSeats - remainingSeats

  const handleTotalSeatsChange = (val) => {
    const v = Math.max(1, Number(val))
    setTotalSeats(v)
    if (remainingSeats > v) setRemainingSeats(v)
  }

  const handleRemainingSeatsChange = (val) => {
    const v = Math.min(Math.max(1, Number(val)), totalSeats)
    setRemainingSeats(v)
  }

  const handleStart = async () => {
    if (remainingSeats > totalSeats) { setError('남은 좌석이 총 정원보다 많을 수 없습니다.'); return }
    setError('')
    setLoading(true)
    const autoNickname = `user_${Math.floor(Math.random() * 9000) + 1000}`
    try {
      const course = await getRandomCourse()
      sessionStorage.removeItem('virtualTime')
      sessionStorage.removeItem('virtualStartReal')
      sessionStorage.removeItem('aquarush_simId')
      sessionStorage.removeItem('aquarush_meta')
      sessionStorage.setItem('aquarush_config', JSON.stringify({
        nickname: autoNickname,
        botCount,
        courseId: course.id,
        totalSeats,
        remainingSeats,
      }))
      navigate('/registration')
    } catch {
      setError('강좌 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="start-page">
      <div className="aqua-logo">
        <h1>🏊 Aqua Rush</h1>
      </div>
      <div className="start-card">
        <div className="start-card-title">🎫 수영 티켓팅 시뮬레이터</div>

        {/* Bot count presets */}
        <div className="form-group">
          <label>경쟁 난이도 (봇 수)</label>
          <div className="preset-grid">
            {PRESETS.map(preset => (
              <button
                key={preset.label}
                className={`preset-btn ${botCount === preset.botCount ? 'active' : ''}`}
                onClick={() => setBotCount(preset.botCount)}
              >
                <span className="preset-label">{preset.label}</span>
                <span className="preset-desc">{preset.description}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Capacity settings */}
        <div className="form-group">
          <label>경쟁 조건 설정</label>
          <div className="capacity-grid">
            <div className="capacity-field">
              <span className="capacity-label">총 정원</span>
              <div className="capacity-input-wrap">
                <button className="cap-btn" onClick={() => handleTotalSeatsChange(totalSeats - 1)}>−</button>
                <input
                  type="number"
                  className="capacity-input"
                  value={totalSeats}
                  min={1}
                  max={1000}
                  onChange={e => handleTotalSeatsChange(e.target.value)}
                />
                <button className="cap-btn" onClick={() => handleTotalSeatsChange(totalSeats + 1)}>+</button>
              </div>
              <span className="capacity-unit">석</span>
            </div>

            <div className="capacity-divider">중</div>

            <div className="capacity-field">
              <span className="capacity-label">남은 좌석</span>
              <div className="capacity-input-wrap">
                <button className="cap-btn" onClick={() => handleRemainingSeatsChange(remainingSeats - 1)}>−</button>
                <input
                  type="number"
                  className="capacity-input"
                  value={remainingSeats}
                  min={1}
                  max={totalSeats}
                  onChange={e => handleRemainingSeatsChange(e.target.value)}
                />
                <button className="cap-btn" onClick={() => handleRemainingSeatsChange(remainingSeats + 1)}>+</button>
              </div>
              <span className="capacity-unit">석</span>
            </div>
          </div>
          <div className="capacity-summary">
            현재 <strong>{filledSeats}명</strong> 등록 완료 · 남은 좌석 <strong className="highlight">{remainingSeats}석</strong> / 총 <strong>{totalSeats}석</strong>
          </div>
        </div>

        {error && <p className="error-msg">{error}</p>}

        <button className="start-btn" onClick={handleStart} disabled={loading}>
          {loading ? '강좌 배정 중...' : '티켓팅 시작하기'}
        </button>
      </div>
    </div>
  )
}
