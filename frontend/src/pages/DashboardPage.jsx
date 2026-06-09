import { useEffect, useState, useRef } from 'react'
import { useParams, useLocation, useNavigate } from 'react-router-dom'
import { createSseConnection, stopSimulation, getStatus } from '../api/simulation'
import StatCard from '../components/StatCard'
import QueueBar from '../components/QueueBar'
import './DashboardPage.css'

export default function DashboardPage() {
  const { simulationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [status, setStatus] = useState(location.state?.status || null)
  const [stopping, setStopping] = useState(false)
  const esRef = useRef(null)

  useEffect(() => {
    esRef.current = createSseConnection(
      simulationId,
      (data) => setStatus(data),
      () => {
        getStatus(simulationId).then(finalStatus => {
          navigate(`/result/${simulationId}`, { state: { status: finalStatus } })
        })
      },
      () => {
        getStatus(simulationId).then(finalStatus => {
          navigate(`/result/${simulationId}`, { state: { status: finalStatus } })
        })
      }
    )

    return () => esRef.current?.close()
  }, [simulationId, navigate])

  const handleStop = async () => {
    setStopping(true)
    try {
      await stopSimulation(simulationId)
    } catch (e) {
      setStopping(false)
    }
  }

  if (!status) {
    return <div className="loading-screen"><div className="spinner" />접속 중...</div>
  }

  const successRate = status.totalParticipants > 0
    ? Math.round((status.successCount / status.totalParticipants) * 100)
    : 0

  return (
    <>
    <div className="aqua-header">🏊 Aqua Rush</div>
    <div className="dashboard-page">
      <div className="dashboard-header">
        <div>
          <h1>🎫 {status.courseName}</h1>
          <p className="my-nickname">참가자: <strong>{status.myNickname}</strong></p>
        </div>
        <div className="header-right">
          <span className={`status-badge ${status.status.toLowerCase()}`}>
            {status.status === 'RUNNING' ? '🔴 진행 중' : status.status}
          </span>
          {status.status === 'RUNNING' && (
            <button className="stop-btn" onClick={handleStop} disabled={stopping}>
              {stopping ? '종료 중...' : '시뮬레이션 종료'}
            </button>
          )}
        </div>
      </div>

      <div className="stats-grid">
        <StatCard label="총 참가자" value={`${status.totalParticipants}명`} icon="👥" />
        <StatCard label="예약 성공" value={`${status.successCount}명`} icon="✅" highlight />
        <StatCard label="예약 실패" value={`${status.failCount}명`} icon="❌" />
        <StatCard label="남은 좌석" value={`${status.remainingSeats}석`} icon="🪑" />
      </div>

      <div className="queue-section">
        <h2>대기열 현황</h2>
        <QueueBar
          queueLength={status.queueLength}
          totalParticipants={status.totalParticipants}
          myRank={status.myRank}
        />
        <div className="queue-info">
          {status.myRank ? (
            <>
              <span className="my-rank">내 순번: <strong>{status.myRank}번</strong></span>
              <span className="wait-time">예상 대기: <strong>{status.estimatedWaitTime}초</strong></span>
            </>
          ) : (
            <span className="queue-done">대기열 처리 완료</span>
          )}
          <span className="queue-total">대기 중: {status.queueLength}명</span>
        </div>
      </div>

      <div className="progress-section">
        <h2>예약 진행률</h2>
        <div className="progress-bar-wrap">
          <div className="progress-bar" style={{ width: `${successRate}%` }} />
        </div>
        <p className="progress-label">
          {status.successCount} / {status.totalParticipants}명 예약 완료 ({successRate}%)
        </p>
      </div>
    </div>
    </>
  )
}
