import { useState, useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import AquaHeader from '../components/AquaHeader'
import QueueModal from '../components/QueueModal'
import AccessQueueOverlay from '../components/AccessQueueOverlay'
import { useVirtualClock } from '../hooks/useVirtualClock'
import { startSimulation } from '../api/simulation'
import './RegistrationPage.css'

const DUMMY_COURSES = [
  { id: 99, center: '센터 B', category: '헬스', name: '크로스핏 초급반', time: '화,목 19:00-20:00', target: '성인', enrolled: 12, capacity: 12, price: 120000 },
  { id: 98, center: '센터 C', category: '요가', name: '하타요가 중급반', time: '월,수,금 10:00-11:00', target: '성인', enrolled: 8, capacity: 15, price: 90000 },
  { id: 97, center: '센터 D', category: '키즈', name: '어린이 수영교실', time: '토 11:00-12:00', target: '어린이', enrolled: 6, capacity: 10, price: 70000 },
]

function loadConfig() {
  try { return JSON.parse(sessionStorage.getItem('aquarush_config') || '{}') }
  catch { return {} }
}

function loadMeta() {
  try {
    const saved = sessionStorage.getItem('aquarush_meta')
    return saved ? JSON.parse(saved) : null
  } catch { return null }
}

export default function RegistrationPage() {
  const location = useLocation()
  const navigate = useNavigate()

  const config = { ...loadConfig(), ...(location.state || {}) }
  const { nickname, botCount = 100, courseId = 1 } = config

  const { isOpen, secondsUntilOpen } = useVirtualClock()

  // Was the page already past 9:00 when it first mounted? (= user refreshed after open)
  const [openOnMount] = useState(() => isOpen)

  const savedSimId = sessionStorage.getItem('aquarush_simId')
  const savedMeta = loadMeta()

  const [currentSimId, setCurrentSimId] = useState(savedSimId)
  const [missionMeta, setMissionMeta] = useState(savedMeta || { name: '성인 자유수영', capacity: 20 })

  // Always starts false; becomes true only after AccessQueueOverlay completes on refresh
  const [accessGranted, setAccessGranted] = useState(false)

  const [cart, setCart] = useState([])
  const [toast, setToast] = useState('')
  const [queueTarget, setQueueTarget] = useState(null)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState('')
  const [retryCount, setRetryCount] = useState(0)

  // Prevent calling startSimulation more than once per page session
  const autoStartedRef = useRef(!!savedSimId)

  // Auto-start bots the moment 9:00 hits (once per page session)
  // retryCount 변경 시에도 재실행되어 재시도가 동작함
  useEffect(() => {
    if (!isOpen) return
    if (autoStartedRef.current) return
    autoStartedRef.current = true

    setStarting(true)
    setStartError('')
    startSimulation(courseId, botCount, nickname)
      .then(data => {
        const sid = data.simulationId
        const name = data.courseName || '성인 자유수영'
        const cap = data.totalSeats || 20
        sessionStorage.setItem('aquarush_simId', sid)
        sessionStorage.setItem('aquarush_meta', JSON.stringify({ name, capacity: cap }))
        setCurrentSimId(sid)
        setMissionMeta({ name, capacity: cap })
        setStarting(false)
      })
      .catch(() => {
        setStartError('시뮬레이션 시작에 실패했습니다. 잠시 후 다시 시도해주세요.')
        setStarting(false)
        autoStartedRef.current = false
      })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, retryCount])

  const missionCourse = {
    id: courseId,
    center: '센터 A',
    category: '수영',
    name: missionMeta.name,
    time: '월,수,금 14:00-15:00',
    target: '성인',
    enrolled: 0,
    capacity: missionMeta.capacity,
    price: 80000,
  }

  const allCourses = [missionCourse, ...DUMMY_COURSES]

  const showToast = (msg) => {
    setToast(msg)
    setTimeout(() => setToast(''), 2500)
  }

  const handleMissionClick = () => {
    if (!accessGranted || starting || !!queueTarget || !currentSimId) return
    setQueueTarget({
      id: courseId,
      center: '센터 A',
      category: '수영',
      name: missionMeta.name,
      time: '월,수,금 14:00-15:00',
      target: '성인',
      enrolled: 0,
      capacity: missionMeta.capacity,
      price: 80000,
    })
  }

  const handleCartClick = (course) => {
    if (!accessGranted) return
    if (course.enrolled >= course.capacity) return
    if (cart.find(c => c.id === course.id)) { showToast('이미 장바구니에 있는 강좌입니다.'); return }
    setCart(prev => [...prev, course])
    showToast('🎉 장바구니에 추가되었습니다!')
  }

  const handleQueueConfirm = (finalStatus) => {
    const course = queueTarget
    setQueueTarget(null)
    if (finalStatus?.myReservationSuccess) {
      navigate(`/cart/${currentSimId}`, {
        state: { nickname, botCount, courseId, simulationId: currentSimId, cart: [...cart, course], finalStatus },
      })
    } else {
      navigate(`/result/${currentSimId}`, { state: { status: finalStatus } })
    }
  }

  const goToCart = () => {
    if (!currentSimId) return
    navigate(`/cart/${currentSimId}`, {
      state: { nickname, botCount, courseId, simulationId: currentSimId, cart },
    })
  }

  const missionBtnText = () => {
    if (!isOpen) return `🔒 ${secondsUntilOpen}초`
    if (!accessGranted) return '🔄 새로고침 필요'
    if (starting || !currentSimId) return '준비 중...'
    return '신청하기'
  }

  // Show access queue: only when page was opened after 9:00, sim exists, not yet granted
  const showAccessQueue = openOnMount && !!currentSimId && !accessGranted

  return (
    <>
      {showAccessQueue && (
        <AccessQueueOverlay onComplete={() => setAccessGranted(true)} />
      )}

      <AquaHeader step={0} cartCount={cart.length} onCartClick={goToCart} />

      <div className="reg-container">
        <div className="page-header">
          <h1 className="page-title">🎯 수강신청</h1>
          <p className="page-subtitle">원하시는 강좌를 편리하게 온라인으로 신청하세요</p>
        </div>

        <div className="mission-box">
          <div className="mission-title">🎯 나의 미션 강좌</div>
          <div className="mission-desc">아래 강좌를 찾아 신청 버튼을 눌러 경쟁에 참여하세요!</div>
          <div className="mission-items">
            <div className="mission-item"><span className="mission-label">참가자</span><strong>{nickname}</strong></div>
            <div className="mission-item"><span className="mission-label">강좌명</span><strong className="highlight">{missionCourse.name}</strong></div>
            <div className="mission-item"><span className="mission-label">센터</span><strong>{missionCourse.center}</strong></div>
            <div className="mission-item"><span className="mission-label">시간</span><strong>{missionCourse.time}</strong></div>
            <div className="mission-item"><span className="mission-label">정원</span><strong>{missionCourse.capacity}명</strong></div>
          </div>
        </div>

        {/* Status banners */}
        {!isOpen && (
          <div className="countdown-box">
            <span className="countdown-icon">🔒</span>
            <span>수강신청 오픈까지 <strong>{secondsUntilOpen}초</strong> 남았습니다 — 09:00:00 오픈</span>
          </div>
        )}

        {isOpen && !openOnMount && !accessGranted && (
          <div className="refresh-notice-box">
            🔄 수강신청이 오픈되었습니다! 봇이 경쟁을 시작했습니다.{' '}
            <strong>F5(새로고침)</strong>을 눌러 버튼을 활성화하세요.
          </div>
        )}

        {isOpen && accessGranted && !startError && (
          <div className="open-box">
            ✅ 접속이 완료되었습니다! 미션 강좌의 <strong>신청하기</strong> 버튼을 누르세요!
          </div>
        )}

        {startError && (
          <div className="error-box">
            <span>❌ {startError}</span>
            <button className="retry-btn" onClick={() => {
              setStartError('')
              autoStartedRef.current = false
              setCurrentSimId(null)
              sessionStorage.removeItem('aquarush_simId')
              setRetryCount(c => c + 1)
            }}>재시도</button>
          </div>
        )}

        <div className="schedule-section">
          <h2 className="section-title">📅 접수일정 안내</h2>
          <table className="schedule-table">
            <thead>
              <tr><th>센터명</th><th>재등록</th><th>신규등록</th><th>수강 신청 시간</th></tr>
            </thead>
            <tbody>
              <tr><td>센터 A</td><td>매달 15일 00시 ~ 20일</td><td>매달 23일 09시 ~ 말일</td><td>오전 9시</td></tr>
              <tr><td>센터 B</td><td>매달 16일 00시 ~ 21일</td><td>매달 24일 10시 ~ 말일</td><td>오전 7시</td></tr>
              <tr><td>센터 C</td><td>매달 16일 00시 ~ 21일</td><td>매달 24일 07시 ~ 말일</td><td>오전 9시</td></tr>
              <tr><td>센터 D</td><td>매달 15일 05시 ~ 20일</td><td>매달 25일 09시 ~ 말일</td><td>오전 7시</td></tr>
            </tbody>
          </table>
        </div>

        <div className="results-section">
          <h2 className="section-title">📋 강좌 목록</h2>
          <div className="table-wrap">
            <table className="results-table">
              <thead>
                <tr>
                  <th>센터</th><th>분류</th><th>강좌명</th><th>시간</th>
                  <th>대상</th><th>등록/정원</th><th>수강료</th><th>신청</th>
                </tr>
              </thead>
              <tbody>
                {allCourses.map(course => {
                  const isFull = course.enrolled >= course.capacity
                  const isMission = course.id === missionCourse.id
                  const inCart = !!cart.find(c => c.id === course.id)
                  return (
                    <tr key={course.id} className={isMission ? 'mission-row' : ''}>
                      <td>{course.center}</td>
                      <td>{course.category}</td>
                      <td>
                        {course.name}
                        {isMission && <span className="mission-badge">미션</span>}
                      </td>
                      <td>{course.time}</td>
                      <td>{course.target}</td>
                      <td className={isFull ? 'status-full' : 'status-available'}>
                        {course.enrolled}/{course.capacity}
                      </td>
                      <td className="price">{course.price.toLocaleString()}원</td>
                      <td>
                        {isMission ? (
                          <button
                            className={`add-cart-btn ${!accessGranted ? 'locked' : ''}`}
                            disabled={!accessGranted || starting || !!queueTarget || !currentSimId}
                            onClick={handleMissionClick}
                          >
                            {missionBtnText()}
                          </button>
                        ) : (
                          <button
                            className={`add-cart-btn ${!accessGranted || isFull ? 'locked' : ''}`}
                            disabled={!accessGranted || isFull || inCart}
                            onClick={() => handleCartClick(course)}
                          >
                            {isFull ? '마감' : inCart ? '담김 ✓' : !accessGranted ? '🔒' : '장바구니'}
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {queueTarget && currentSimId && (
        <QueueModal
          simulationId={currentSimId}
          course={queueTarget}
          onConfirm={handleQueueConfirm}
          onClose={() => {}}
        />
      )}

      {toast && <div className="toast show">{toast}</div>}
    </>
  )
}
