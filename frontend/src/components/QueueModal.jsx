import { useEffect, useRef, useState } from 'react'
import { createSseConnection, getStatus } from '../api/simulation'
import './QueueModal.css'

export default function QueueModal({ simulationId, course, onConfirm, onClose }) {
  const [status, setStatus] = useState(null)
  const [done, setDone] = useState(false)
  const esRef = useRef(null)
  const pollRef = useRef(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true

    const finish = (finalStatus) => {
      if (!mountedRef.current) return
      setStatus(finalStatus)
      setDone(true)
      esRef.current?.close()
      clearInterval(pollRef.current)
    }

    // 1.5초마다 상태 폴링 (SSE 누락·경쟁조건 대비)
    const startPolling = () => {
      pollRef.current = setInterval(() => {
        getStatus(simulationId).then(s => {
          if (!mountedRef.current) return
          setStatus(s)
          if (s.status === 'COMPLETED' || s.status === 'STOPPED') {
            clearInterval(pollRef.current)
            finish(s)
          }
        }).catch(() => {})
      }, 1500)
    }

    getStatus(simulationId)
      .then(s => {
        if (!mountedRef.current) return
        setStatus(s)
        if (s.status === 'COMPLETED' || s.status === 'STOPPED') {
          setDone(true)
          return
        }
        esRef.current = createSseConnection(
          simulationId,
          (data) => { if (mountedRef.current) setStatus(data) },
          () => getStatus(simulationId).then(finish).catch(() => {}),
          () => getStatus(simulationId).then(finish).catch(() => {})
        )
        startPolling()
      })
      .catch(() => {
        if (!mountedRef.current) return
        esRef.current = createSseConnection(
          simulationId,
          (data) => { if (mountedRef.current) setStatus(data) },
          () => getStatus(simulationId).then(finish).catch(() => {}),
          () => getStatus(simulationId).then(finish).catch(() => {})
        )
        startPolling()
      })

    return () => {
      mountedRef.current = false
      esRef.current?.close()
      clearInterval(pollRef.current)
    }
  }, [simulationId])

  const myRank = status?.myRank
  const queueLength = status?.queueLength ?? 0
  const totalParticipants = status?.totalParticipants ?? 1
  const successCount = status?.successCount ?? 0
  const remainingSeats = status?.remainingSeats ?? 0
  const mySuccess = status?.myReservationSuccess

  const queueProgress = totalParticipants > 0
    ? Math.round(((totalParticipants - queueLength) / totalParticipants) * 100)
    : 0

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="queue-modal" onClick={e => e.stopPropagation()}>

        {!done ? (
          <>
            <div className="modal-header">
              <div className="modal-icon spinning">⏳</div>
              <h2>대기 중입니다</h2>
              <p className="modal-course">{course.name} · {course.center}</p>
            </div>

            <div className="rank-box">
              {myRank ? (
                <>
                  <div className="rank-label">현재 내 순번</div>
                  <div className="rank-number">{myRank}<span>번째</span></div>
                  <div className="rank-sub">내 앞에 <strong>{myRank - 1}명</strong> 대기 중</div>
                </>
              ) : (
                <>
                  <div className="rank-label">대기열 처리 중</div>
                  <div className="rank-number processing">처리 중</div>
                </>
              )}
            </div>

            <div className="queue-stats">
              <div className="stat-item">
                <span className="stat-label">전체 참가자</span>
                <span className="stat-val">{totalParticipants}명</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">대기 중</span>
                <span className="stat-val waiting">{queueLength}명</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">예약 완료</span>
                <span className="stat-val success">{successCount}명</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">남은 좌석</span>
                <span className="stat-val seats">{remainingSeats}석</span>
              </div>
            </div>

            <div className="queue-progress-wrap">
              <div className="queue-progress-label">
                <span>대기열 소진률</span>
                <span>{queueProgress}%</span>
              </div>
              <div className="queue-progress-track">
                <div className="queue-progress-fill" style={{ width: `${queueProgress}%` }} />
              </div>
            </div>

            <div className="modal-notice">🔄 실시간으로 업데이트 중입니다</div>
          </>
        ) : (
          <>
            <div className="modal-header">
              <div className="modal-icon">{mySuccess ? '🎉' : '😢'}</div>
              <h2>{mySuccess ? '예약 성공!' : '예약 실패'}</h2>
              <p className="modal-course">
                {mySuccess
                  ? `${status.myPosition}번째로 예약이 확정되었습니다!`
                  : '아쉽게도 정원이 마감되었습니다.'}
              </p>
            </div>

            <div className={`result-summary ${mySuccess ? 'success' : 'fail'}`}>
              <div className="stat-item">
                <span className="stat-label">예약 성공</span>
                <span className="stat-val success">{status?.successCount}명</span>
              </div>
              <div className="stat-item">
                <span className="stat-label">예약 실패</span>
                <span className="stat-val fail-color">{status?.failCount}명</span>
              </div>
            </div>

            <button className="confirm-btn" onClick={() => onConfirm(status)}>
              다음으로 →
            </button>
          </>
        )}
      </div>
    </div>
  )
}
