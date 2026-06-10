import { useState, useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import AquaHeader from '../components/AquaHeader'
import QueueModal from '../components/QueueModal'
import AccessQueueOverlay from '../components/AccessQueueOverlay'
import { useVirtualClock } from '../hooks/useVirtualClock'
import { startSimulation, getCenters, getCategories, getCourses, getCourseDetail } from '../api/simulation'
import './RegistrationPage.css'

const LEVELS = ['초급', '중급', '고급']
const TARGETS = ['성인/청소년', '어린이']

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

function metaFromCourseDetail(course) {
  return {
    name: course.name,
    centerName: course.centerName,
    weekdays: course.schedule?.weekdays,
    timeSlot: course.schedule?.timeSlot,
    level: course.level,
    targetAudience: course.targetAudience,
    capacity: course.capacity?.max,
  }
}

export default function RegistrationPage() {
  const location = useLocation()
  const navigate = useNavigate()

  const config = { ...loadConfig(), ...(location.state || {}) }
  const { nickname, botCount = 100, courseId = 1 } = config

  const { isOpen, secondsUntilOpen } = useVirtualClock()
  const [openOnMount] = useState(() => isOpen)

  const savedSimId = sessionStorage.getItem('aquarush_simId')
  const [currentSimId, setCurrentSimId] = useState(savedSimId)
  const [missionMeta, setMissionMeta] = useState(loadMeta())

  // Bug fix: removed !!currentSimId — overlay shows immediately on refresh without waiting for sim start
  const [accessGranted, setAccessGranted] = useState(false)

  const [cart, setCart] = useState([])
  const [toast, setToast] = useState('')
  const [queueTarget, setQueueTarget] = useState(null)
  const [starting, setStarting] = useState(false)
  const [startError, setStartError] = useState('')
  const [retryCount, setRetryCount] = useState(0)

  const [centers, setCenters] = useState([])
  const [categories, setCategories] = useState([])
  const [courses, setCourses] = useState([])
  const [selectedCenterId, setSelectedCenterId] = useState(null)
  const [selectedCategoryId, setSelectedCategoryId] = useState(null)
  const [selectedLevel, setSelectedLevel] = useState(null)
  const [selectedTarget, setSelectedTarget] = useState(null)
  const [coursesLoading, setCoursesLoading] = useState(false)

  const autoStartedRef = useRef(!!savedSimId)

  // Load mission course detail if not in sessionStorage yet
  useEffect(() => {
    if (!missionMeta && courseId) {
      getCourseDetail(courseId)
        .then(course => setMissionMeta(metaFromCourseDetail(course)))
        .catch(() => {})
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Load centers and categories on mount
  useEffect(() => {
    getCenters().then(data => setCenters(data.centers || [])).catch(() => {})
    getCategories().then(data => setCategories(data.categories || [])).catch(() => {})
  }, [])

  // Re-fetch courses when filters change
  useEffect(() => {
    setCoursesLoading(true)
    const params = {}
    if (selectedCenterId) params.centerId = selectedCenterId
    if (selectedCategoryId) params.categoryId = selectedCategoryId
    if (selectedLevel) params.level = selectedLevel
    if (selectedTarget) params.targetAudience = selectedTarget
    getCourses(params)
      .then(data => setCourses(data.courses || []))
      .catch(() => setCourses([]))
      .finally(() => setCoursesLoading(false))
  }, [selectedCenterId, selectedCategoryId, selectedLevel, selectedTarget])

  // Auto-start simulation when 09:00 hits
  useEffect(() => {
    if (!isOpen) return
    if (autoStartedRef.current) return
    autoStartedRef.current = true

    setStarting(true)
    setStartError('')
    startSimulation(courseId, botCount, nickname)
      .then(data => {
        const sid = data.simulationId
        const meta = {
          name: data.courseName,
          centerName: data.centerName,
          weekdays: data.weekdays,
          timeSlot: data.timeSlot,
          level: data.level,
          targetAudience: data.targetAudience,
          capacity: data.totalSeats || 20,
        }
        sessionStorage.setItem('aquarush_simId', sid)
        sessionStorage.setItem('aquarush_meta', JSON.stringify(meta))
        setCurrentSimId(sid)
        setMissionMeta(meta)
        setStarting(false)
      })
      .catch(() => {
        setStartError('시뮬레이션 시작에 실패했습니다. 잠시 후 다시 시도해주세요.')
        setStarting(false)
        autoStartedRef.current = false
      })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, retryCount])

  const showToast = (msg) => {
    setToast(msg)
    setTimeout(() => setToast(''), 2500)
  }

  const handleMissionClick = () => {
    if (!accessGranted || starting || !!queueTarget || !currentSimId || !missionMeta) return
    setQueueTarget({
      id: courseId,
      center: missionMeta.centerName || '',
      category: '수영',
      name: missionMeta.name || '',
      time: `${missionMeta.weekdays || ''} ${missionMeta.timeSlot || ''}`.trim(),
      target: missionMeta.targetAudience || '',
      enrolled: 0,
      capacity: missionMeta.capacity || 20,
      price: 0,
    })
  }

  const handleCartClick = (course) => {
    if (!accessGranted) return
    if (!course.isAvailable) return
    if (cart.find(c => c.id === course.id)) { showToast('이미 장바구니에 있는 강좌입니다.'); return }
    setCart(prev => [...prev, course])
    showToast('장바구니에 추가되었습니다!')
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
    if (!accessGranted) return '🔄 새로고침'
    if (starting || !currentSimId) return '준비 중...'
    return '신청하기'
  }

  const showAccessQueue = openOnMount && !accessGranted

  return (
    <>
      {showAccessQueue && (
        <AccessQueueOverlay onComplete={() => setAccessGranted(true)} />
      )}

      <AquaHeader step={0} cartCount={cart.length} onCartClick={goToCart} />

      <div className="reg-container">
        <div className="page-header">
          <h1 className="page-title">수강신청</h1>
          <p className="page-subtitle">원하시는 강좌를 편리하게 온라인으로 신청하세요</p>
        </div>

        {/* Mission box */}
        {missionMeta && (
          <div className="mission-box">
            <div className="mission-header">
              <div>
                <div className="mission-title">🎯 나의 미션 강좌</div>
                <div className="mission-desc">아래 강좌를 찾아 신청 버튼을 눌러 경쟁에 참여하세요!</div>
              </div>
              {isOpen && accessGranted && !startError && (
                <button
                  className="mission-apply-btn"
                  disabled={starting || !!queueTarget || !currentSimId}
                  onClick={handleMissionClick}
                >
                  {starting || !currentSimId ? '준비 중...' : '신청하기'}
                </button>
              )}
            </div>
            <div className="mission-items">
              <div className="mission-item"><span className="mission-label">참가자</span><strong>{nickname}</strong></div>
              <div className="mission-item"><span className="mission-label">강좌명</span><strong className="highlight">{missionMeta.name}</strong></div>
              {missionMeta.centerName && <div className="mission-item"><span className="mission-label">센터</span><strong>{missionMeta.centerName}</strong></div>}
              {missionMeta.weekdays && <div className="mission-item"><span className="mission-label">요일</span><strong>{missionMeta.weekdays}</strong></div>}
              {missionMeta.timeSlot && <div className="mission-item"><span className="mission-label">시간</span><strong>{missionMeta.timeSlot}</strong></div>}
              {missionMeta.level && <div className="mission-item"><span className="mission-label">레벨</span><strong>{missionMeta.level}</strong></div>}
              {missionMeta.targetAudience && <div className="mission-item"><span className="mission-label">대상</span><strong>{missionMeta.targetAudience}</strong></div>}
              {missionMeta.capacity && <div className="mission-item"><span className="mission-label">정원</span><strong>{missionMeta.capacity}명</strong></div>}
            </div>
          </div>
        )}

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
            ✅ 접속 완료! 강좌 목록에서 미션 강좌를 찾아 <strong>신청하기</strong> 버튼을 누르세요.
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

        {/* Filter UI */}
        <div className="filter-section">
          <div className="filter-row">
            <span className="filter-label">체육센터</span>
            <div className="filter-tabs">
              <button
                className={`filter-tab ${selectedCenterId === null ? 'active' : ''}`}
                onClick={() => setSelectedCenterId(null)}
              >전체</button>
              {centers.map(c => (
                <button
                  key={c.id}
                  className={`filter-tab ${selectedCenterId === c.id ? 'active' : ''}`}
                  onClick={() => setSelectedCenterId(c.id)}
                >
                  {c.name}
                </button>
              ))}
            </div>
          </div>

          <div className="filter-row">
            <span className="filter-label">대분류</span>
            <div className="filter-tabs">
              <button
                className={`filter-tab ${selectedCategoryId === null ? 'active' : ''}`}
                onClick={() => setSelectedCategoryId(null)}
              >전체</button>
              {categories.map(cat => (
                <button
                  key={cat.id}
                  className={`filter-tab ${selectedCategoryId === cat.id ? 'active' : ''}`}
                  onClick={() => setSelectedCategoryId(cat.id)}
                >
                  {cat.name}
                </button>
              ))}
            </div>
          </div>

          <div className="filter-row">
            <span className="filter-label">소분류</span>
            <div className="filter-chips">
              <button
                className={`filter-chip ${selectedLevel === null ? 'active' : ''}`}
                onClick={() => setSelectedLevel(null)}
              >전체</button>
              {LEVELS.map(l => (
                <button
                  key={l}
                  className={`filter-chip ${selectedLevel === l ? 'active' : ''}`}
                  onClick={() => setSelectedLevel(selectedLevel === l ? null : l)}
                >
                  {l}
                </button>
              ))}
            </div>
          </div>

          <div className="filter-row">
            <span className="filter-label">교육 대상</span>
            <div className="filter-chips">
              <button
                className={`filter-chip ${selectedTarget === null ? 'active' : ''}`}
                onClick={() => setSelectedTarget(null)}
              >전체</button>
              {TARGETS.map(t => (
                <button
                  key={t}
                  className={`filter-chip ${selectedTarget === t ? 'active' : ''}`}
                  onClick={() => setSelectedTarget(selectedTarget === t ? null : t)}
                >
                  {t}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Course list */}
        <div className="results-section">
          <h2 className="section-title">
            📋 강좌 목록
            {!coursesLoading && <span className="course-count"> ({courses.length}개)</span>}
          </h2>
          {coursesLoading ? (
            <div className="courses-loading">강좌를 불러오는 중...</div>
          ) : courses.length === 0 ? (
            <div className="courses-empty">해당 조건의 강좌가 없습니다.</div>
          ) : (
            <div className="table-wrap">
              <table className="results-table">
                <thead>
                  <tr>
                    <th>센터</th><th>분류</th><th>강좌명</th><th>요일</th><th>시간</th>
                    <th>레벨</th><th>대상</th><th>등록/정원</th><th>수강료</th><th>신청</th>
                  </tr>
                </thead>
                <tbody>
                  {courses.map(course => {
                    const isMission = course.id === courseId
                    const isFull = !course.isAvailable
                    const inCart = !!cart.find(c => c.id === course.id)
                    return (
                      <tr key={course.id} className={isMission ? 'mission-row' : ''}>
                        <td>{course.centerName}</td>
                        <td>{course.categoryName}</td>
                        <td>
                          {course.courseName}
                          {isMission && <span className="mission-badge">미션</span>}
                        </td>
                        <td>{course.weekdays}</td>
                        <td>{course.timeSlot}</td>
                        <td>{course.level}</td>
                        <td>{course.targetAudience}</td>
                        <td className={isFull ? 'status-full' : 'status-available'}>
                          {course.currentCapacity}/{course.maxCapacity}
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
          )}
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
