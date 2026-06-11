import { useState, useEffect, useRef } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import AquaHeader from '../components/AquaHeader'
import AccessQueueOverlay from '../components/AccessQueueOverlay'
import { useVirtualClock } from '../hooks/useVirtualClock'
import { startSimulation, getCenters, getCategories, getCourses, getCourseDetail, enterAccessQueue, getAccessQueueStatus } from '../api/simulation'
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
    price: course.price,
  }
}

export default function RegistrationPage() {
  const location = useLocation()
  const navigate = useNavigate()

  const config = { ...loadConfig(), ...(location.state || {}) }
  const { nickname, botCount = 100, courseId = 1, totalSeats, remainingSeats } = config

  const { isOpen, secondsUntilOpen, virtualMs } = useVirtualClock()

  const savedSimId = sessionStorage.getItem('aquarush_simId')
  const [currentSimId, setCurrentSimId] = useState(savedSimId)
  const [missionMeta, setMissionMeta] = useState(loadMeta())

  const [accessGranted, setAccessGranted] = useState(false)
  const [queueToken, setQueueToken] = useState(null)
  const [queuePosition, setQueuePosition] = useState(0)
  const [initialQueuePosition, setInitialQueuePosition] = useState(0)
  const [estimatedWaitSeconds, setEstimatedWaitSeconds] = useState(0)
  const queueEnteredRef = useRef(false)

  const [courseRefreshTrigger, setCourseRefreshTrigger] = useState(0)
  const [cart, setCart] = useState([])
  const [toast, setToast] = useState('')
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

  // 페이지 진입마다 새 대기열 생성 (새로고침 = 맨 뒤로)
  useEffect(() => {
    if (queueEnteredRef.current) return
    queueEnteredRef.current = true

    enterAccessQueue(botCount, virtualMs)
      .then(data => {
        setQueueToken(data.queueToken)
        setQueuePosition(data.position)
        setInitialQueuePosition(data.initialPosition)
        setEstimatedWaitSeconds(data.estimatedWaitSeconds)
      })
      .catch(() => {
        setAccessGranted(true)
      })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // 9시 이후 1초 간격으로 대기열 상태 폴링
  useEffect(() => {
    if (!queueToken || accessGranted) return
    const id = setInterval(async () => {
      try {
        const status = await getAccessQueueStatus(queueToken)
        if (status.isGranted) {
          setAccessGranted(true)
        } else {
          setQueuePosition(status.position)
          setEstimatedWaitSeconds(status.estimatedWaitSeconds)
        }
      } catch { /* 네트워크 오류 시 다음 틱에 재시도 */ }
    }, 1000)
    return () => clearInterval(id)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queueToken, accessGranted, isOpen])

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
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCenterId, selectedCategoryId, selectedLevel, selectedTarget, courseRefreshTrigger])

  // 9시가 되면 시뮬레이션(봇 경쟁) 시작 — 유저 입장은 accessGranted로 별도 제어
  useEffect(() => {
    if (!isOpen) return
    if (autoStartedRef.current) return
    autoStartedRef.current = true

    setStarting(true)
    setStartError('')
    startSimulation(courseId, botCount, nickname, totalSeats, remainingSeats)
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
        setCurrentSimId(sid)
        // price는 getCourseDetail에서 받아온 값 유지 (startSimulation 응답엔 price 없음)
        setMissionMeta(prev => {
          const merged = { ...meta, price: prev?.price }
          sessionStorage.setItem('aquarush_meta', JSON.stringify(merged))
          return merged
        })
        setStarting(false)
        setCourseRefreshTrigger(prev => prev + 1)
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

  // 토큰 확보 즉시 오버레이 표시 (9시 전 순번 대기 → 9시 후 입장 처리)
  const showAccessQueue = queueToken != null && !accessGranted
  const canInteract = isOpen && accessGranted && !starting && !!currentSimId

  const handleMissionClick = () => {
    if (!canInteract || !missionMeta) return
    if (cart.find(c => c.id === courseId)) { showToast('이미 장바구니에 있는 강좌입니다.'); return }
    const missionInList = courses.find(c => c.id === courseId)
    const course = {
      id: courseId,
      center: missionMeta.centerName || '',
      category: '수영',
      name: missionMeta.name || '',
      time: `${missionMeta.weekdays || ''} ${missionMeta.timeSlot || ''}`.trim(),
      target: missionMeta.targetAudience || '',
      price: missionInList?.price ?? missionMeta.price ?? 0,
    }
    setCart(prev => [...prev, course])
    showToast('미션 강좌를 장바구니에 추가했습니다!')
  }

  const normalizeCartItem = (c) => ({
    id: c.id,
    center: c.centerName || c.center || '',
    name: c.courseName || c.name || '',
    time: c.timeSlot ? `${c.weekdays || ''} ${c.timeSlot}`.trim() : (c.time || ''),
    target: c.targetAudience || c.target || '',
    category: c.categoryName || c.category || '',
    price: c.price ?? 0,
  })

  const handleCartClick = (course) => {
    if (!canInteract) return
    if (!course.isAvailable) return
    if (cart.find(c => c.id === course.id)) { showToast('이미 장바구니에 있는 강좌입니다.'); return }
    setCart(prev => [...prev, normalizeCartItem(course)])
    showToast('장바구니에 추가되었습니다!')
  }

  const goToCart = () => {
    if (!currentSimId) return
    navigate(`/cart/${currentSimId}`, {
      state: { nickname, botCount, courseId, simulationId: currentSimId, cart },
    })
  }

  return (
    <>
      {showAccessQueue && (
        <AccessQueueOverlay
          position={queuePosition}
          initialPosition={initialQueuePosition}
          estimatedWaitSeconds={estimatedWaitSeconds}
        />
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
                <div className="mission-desc">아래 강좌를 찾아 <strong>장바구니</strong> 버튼을 눌러 경쟁에 참여하세요!</div>
              </div>
            </div>
            <div className="mission-items">
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


        {isOpen && canInteract && !startError && (
          <div className="open-box">
            ✅ 수강신청 오픈! 미션 강좌를 찾아 <strong>장바구니</strong> 버튼을 누르세요.
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
                    const missionFilled = isMission && totalSeats != null ? totalSeats - (remainingSeats ?? 0) : null
                    return (
                      <tr key={course.id}>
                        <td>{course.centerName}</td>
                        <td>{course.categoryName}</td>
                        <td>{course.courseName}</td>
                        <td>{course.weekdays}</td>
                        <td>{course.timeSlot}</td>
                        <td>{course.level}</td>
                        <td>{course.targetAudience}</td>
                        <td className={!isMission && isFull ? 'status-full' : 'status-available'}>
                          {missionFilled != null
                            ? `${missionFilled}/${totalSeats}`
                            : `${course.currentCapacity}/${course.maxCapacity}`}
                        </td>
                        <td className="price">{course.price.toLocaleString()}원</td>
                        <td>
                          {isMission ? (
                            <button
                              className={`add-cart-btn ${!canInteract ? 'locked' : ''}`}
                              disabled={!canInteract}
                              onClick={handleMissionClick}
                            >
                              {!isOpen ? '🔒' : starting || !currentSimId ? '준비 중...' : inCart ? '담김 ✓' : '장바구니'}
                            </button>
                          ) : (
                            <button
                              className={`add-cart-btn ${!canInteract || isFull ? 'locked' : ''}`}
                              disabled={!canInteract || isFull || inCart}
                              onClick={() => handleCartClick(course)}
                            >
                              {isFull ? '마감' : inCart ? '담김 ✓' : !isOpen ? '🔒' : '장바구니'}
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

      {toast && <div className="toast show">{toast}</div>}
    </>
  )
}
