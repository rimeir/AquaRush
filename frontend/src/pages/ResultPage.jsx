import { useParams, useLocation, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { getStatus } from '../api/simulation'
import AquaHeader from '../components/AquaHeader'
import './ResultPage.css'

export default function ResultPage() {
  const { simulationId } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const [status, setStatus] = useState(location.state?.status || null)

  useEffect(() => {
    if (!status) {
      getStatus(simulationId).then(setStatus).catch(() => navigate('/'))
    }
  }, [simulationId, status, navigate])

  if (!status) {
    return <div className="loading-screen"><div className="spinner" />결과 불러오는 중...</div>
  }

  const success = status.myReservationSuccess
  const successRate = status.totalParticipants > 0
    ? ((status.successCount / status.totalParticipants) * 100).toFixed(1)
    : 0

  const handleRetry = () => {
    sessionStorage.removeItem('virtualTime')
    sessionStorage.removeItem('virtualStartReal')
    sessionStorage.removeItem('aquarush_simId')
    sessionStorage.removeItem('aquarush_meta')
    navigate('/')
  }

  return (
    <>
      <AquaHeader step={3} />

      <div className="result-page">
        <div className={`result-card ${success ? 'success' : 'failure'}`}>
          <div className="result-icon">{success ? '🎉' : '😢'}</div>
          <h1 className="result-title">
            {success ? '수강신청 성공!' : '수강신청 실패'}
          </h1>
          <p className="result-subtitle">
            {success
              ? `축하합니다! ${status.totalParticipants}명 중 ${status.myPosition}번째로 예약 완료!`
              : '아쉽게도 수강 정원이 마감되었습니다.'}
          </p>

          <div className="enrollment-details">
            <h3 className="detail-title">📋 결과 내역</h3>
            <div className="detail-item">
              <span className="detail-label">강좌명</span>
              <span className="detail-value highlight">{status.courseName}</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">총 참가자</span>
              <span className="detail-value">{status.totalParticipants}명</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">예약 성공</span>
              <span className="detail-value success-color">{status.successCount}명</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">예약 실패</span>
              <span className="detail-value fail-color">{status.failCount}명</span>
            </div>
            <div className="detail-item">
              <span className="detail-label">성공률</span>
              <span className="detail-value">{successRate}%</span>
            </div>
            {success && (
              <div className="detail-item">
                <span className="detail-label">내 순위</span>
                <span className="detail-value highlight">🏆 {status.myPosition}위</span>
              </div>
            )}
          </div>

          {!success && (
            <div className="failure-reason-box">
              <div className="failure-reason-title">❌ 실패 사유</div>
              <div className="failure-reason-text">
                수강 인원이 마감되었습니다. (정원 {status.successCount}명 초과)
              </div>
            </div>
          )}

          <div className="btn-group">
            <button className="btn-primary" onClick={handleRetry}>
              다시 도전하기
            </button>
          </div>
        </div>
      </div>

    </>
  )
}
