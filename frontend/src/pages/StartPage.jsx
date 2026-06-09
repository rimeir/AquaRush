import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import './StartPage.css'

const PRESETS = [
  { label: 'Easy', botCount: 100, description: '100명 경쟁 · 20석' },
  { label: 'Normal', botCount: 500, description: '500명 경쟁 · 10석' },
  { label: 'Hard', botCount: 1000, description: '1000명 경쟁 · 5석' },
]

export default function StartPage() {
  const navigate = useNavigate()
  const [nickname, setNickname] = useState('')
  const [botCount, setBotCount] = useState(100)
  const [error, setError] = useState('')

  const handleStart = () => {
    if (!nickname.trim()) {
      setError('닉네임을 입력해주세요.')
      return
    }
    setError('')
    // 이전 세션 데이터 초기화
    sessionStorage.removeItem('virtualTime')
    sessionStorage.removeItem('virtualStartReal')
    sessionStorage.removeItem('aquarush_simId')
    sessionStorage.removeItem('aquarush_meta')
    // 설정 저장 (새로고침 시 복원용)
    sessionStorage.setItem('aquarush_config', JSON.stringify({ nickname: nickname.trim(), botCount, courseId: 1 }))
    navigate('/registration')
  }

  return (
    <div className="start-page">
      <div className="aqua-logo">
        <h1>🏊 Aqua Rush</h1>
      </div>
      <div className="start-card">
        <div className="start-card-title">🎫 수영 티켓팅 시뮬레이터</div>

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

        <div className="form-group">
          <label>닉네임</label>
          <input
            type="text"
            placeholder="닉네임을 입력하세요"
            value={nickname}
            onChange={e => setNickname(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleStart()}
            maxLength={20}
          />
        </div>

        <div className="form-group">
          <label>봇 수 직접 설정 <span className="form-hint">({botCount}명)</span></label>
          <input
            type="range"
            min={10}
            max={1000}
            step={10}
            value={botCount}
            onChange={e => setBotCount(Number(e.target.value))}
          />
          <div className="range-labels">
            <span>10명</span>
            <span>1000명</span>
          </div>
        </div>

        {error && <p className="error-msg">{error}</p>}

        <button className="start-btn" onClick={handleStart}>
          티켓팅 시작하기
        </button>
      </div>
    </div>
  )
}
