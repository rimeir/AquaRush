import { useNavigate } from 'react-router-dom'
import { useVirtualClock } from '../hooks/useVirtualClock'
import './AquaHeader.css'

const STEPS = ['강좌선택', '장바구니', '결제하기', '완료']

export default function AquaHeader({ step, cartCount, onCartClick }) {
  const navigate = useNavigate()
  const { time: clock } = useVirtualClock()

  return (
    <header className="aqua-header">
      <div className="virtual-clock">
        <span className="clock-label">🕐 현재 시간:</span>
        <span className="clock-time">{clock}</span>
      </div>
      <div className="header-content">
        <div className="logo" onClick={() => navigate('/')}>🏊 Aqua Rush</div>
        {cartCount !== undefined && (
          <button className="cart-btn" onClick={onCartClick}>
            🛒 장바구니 <span className="cart-badge">{cartCount}</span>
          </button>
        )}
      </div>
      {step !== undefined && (
        <div className="progress-steps-wrap">
          <div className="progress-steps">
            <div className="progress-line" />
            <div
              className="progress-line-active"
              style={{ width: `${(step / (STEPS.length - 1)) * 84}%` }}
            />
            {STEPS.map((label, i) => (
              <div key={label} className={`step ${i < step ? 'completed' : i === step ? 'active' : ''}`}>
                <div className="step-circle">{i < step ? '✓' : i + 1}</div>
                <div className="step-label">{label}</div>
              </div>
            ))}
          </div>
        </div>
      )}
    </header>
  )
}
