import { useState } from 'react'
import { useParams, useLocation, useNavigate } from 'react-router-dom'
import AquaHeader from '../components/AquaHeader'
import { reserveForUser } from '../api/simulation'
import './CheckoutPage.css'

const PAYMENT_METHODS = [
  { value: 'card', icon: '💳', label: '신용카드' },
  { value: 'transfer', icon: '🏦', label: '계좌이체' },
  { value: 'phone', icon: '📱', label: '휴대폰' },
]

export default function CheckoutPage() {
  const { simulationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const state = location.state || {}
  const cart = state.cart || []
  const missionCourseId = state.courseId

  const [payment, setPayment] = useState('card')
  const [agreed, setAgreed] = useState({ terms: false, privacy: false, refund: false })
  const [processing, setProcessing] = useState(false)
  const [error, setError] = useState('')

  const total = cart.reduce((sum, c) => sum + c.price, 0)
  const allAgreed = agreed.terms && agreed.privacy && agreed.refund

  const toggleAll = (checked) => setAgreed({ terms: checked, privacy: checked, refund: checked })

  const getElapsedSeconds = () => {
    const startReal = parseInt(sessionStorage.getItem('virtualStartReal') || '0')
    const openRealTime = startReal + 30000
    return Math.max(0, Math.floor((Date.now() - openRealTime) / 1000))
  }

  const handlePay = async () => {
    if (!allAgreed || processing) return
    setProcessing(true)

    const elapsedSeconds = getElapsedSeconds()
    const hasMission = cart.some(c => c.id === missionCourseId)

    if (!hasMission) {
      navigate(`/result/${simulationId}`, {
        state: {
          reserved: false,
          failReason: '미션 강좌가 아닌 다른 강좌는 수강신청할 수 없습니다.',
          courseName: cart[0]?.name || '',
          elapsedSeconds,
          totalParticipants: 0,
          successCount: 0,
          failCount: 1,
        },
      })
      return
    }

    try {
      const result = await reserveForUser(simulationId)
      navigate(`/result/${simulationId}`, {
        state: {
          reserved: result.reserved,
          failReason: result.failReason,
          courseName: result.courseName,
          myPosition: result.myPosition,
          totalParticipants: result.totalParticipants,
          successCount: result.successCount,
          failCount: result.failCount,
          elapsedSeconds,
        },
      })
    } catch (e) {
      setProcessing(false)
      setError('결제 처리 중 오류가 발생했습니다. 다시 시도해주세요.')
    }
  }

  return (
    <>
      <AquaHeader step={2} />

      <div className="checkout-container">
        <div className="checkout-layout">
          <div className="form-panel">
            <div className="form-section">
              <h2 className="section-title">💳 결제 방법</h2>
              <div className="payment-methods">
                {PAYMENT_METHODS.map(m => (
                  <label key={m.value} className={`payment-option ${payment === m.value ? 'selected' : ''}`}>
                    <input type="radio" name="payment" value={m.value} checked={payment === m.value} onChange={() => setPayment(m.value)} />
                    <div className="payment-icon">{m.icon}</div>
                    <div className="payment-name">{m.label}</div>
                  </label>
                ))}
              </div>
            </div>

            <div className="form-section">
              <h2 className="section-title">📋 약관 동의</h2>
              <div className="agreement-box">
                <label className="agree-item bold">
                  <input type="checkbox" checked={allAgreed} onChange={e => toggleAll(e.target.checked)} />
                  <span>전체 동의</span>
                </label>
                <label className="agree-item">
                  <input type="checkbox" checked={agreed.terms} onChange={e => setAgreed(a => ({ ...a, terms: e.target.checked }))} />
                  <span><strong className="required">[필수]</strong> 이용약관 동의</span>
                </label>
                <label className="agree-item">
                  <input type="checkbox" checked={agreed.privacy} onChange={e => setAgreed(a => ({ ...a, privacy: e.target.checked }))} />
                  <span><strong className="required">[필수]</strong> 개인정보 수집 및 이용 동의</span>
                </label>
                <label className="agree-item">
                  <input type="checkbox" checked={agreed.refund} onChange={e => setAgreed(a => ({ ...a, refund: e.target.checked }))} />
                  <span><strong className="required">[필수]</strong> 환불 및 취소 정책 동의</span>
                </label>
              </div>
            </div>
          </div>

          <div className="order-summary">
            <h2 className="section-title">📋 주문 내역</h2>
            {cart.map(course => (
              <div key={course.id} className="order-item">
                <div className="order-name">{course.name}</div>
                <div className="order-info">📍 {course.center}</div>
                <div className="order-info">🕐 {course.time}</div>
                <div className="order-price">{course.price.toLocaleString()}원</div>
              </div>
            ))}
            <div className="summary-divider" />
            <div className="summary-row">
              <span>강좌 수</span><span>{cart.length}개</span>
            </div>
            <div className="summary-row total">
              <span>최종 결제금액</span>
              <span className="total-value">{total.toLocaleString()}원</span>
            </div>
            {error && <p style={{ color: '#dc3545', fontSize: '0.9rem', marginBottom: '0.5rem' }}>{error}</p>}
            <button
              className="payment-btn"
              disabled={!allAgreed || processing}
              onClick={handlePay}
            >
              {processing ? '결제 처리 중...' : '결제하기'}
            </button>
          </div>
        </div>
      </div>

    </>
  )
}
