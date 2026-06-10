import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import AquaHeader from '../components/AquaHeader'
import './ResultPage.css'

export default function ResultPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const state = location.state || {}

  useEffect(() => {
    if (state.reserved === undefined) {
      navigate('/')
    }
  }, [state.reserved, navigate])

  if (state.reserved === undefined) return null

  const { reserved, failReason, courseName, myPosition, elapsedSeconds, totalParticipants, successCount, failCount } = state

  const handleRetry = () => {
    sessionStorage.removeItem('virtualTime')
    sessionStorage.removeItem('virtualStartReal')
    sessionStorage.removeItem('aquarush_simId')
    sessionStorage.removeItem('aquarush_meta')
    navigate('/')
  }

  const formatElapsed = (secs) => {
    if (secs == null || secs < 0) return '-'
    if (secs < 60) return `${secs}초`
    return `${Math.floor(secs / 60)}분 ${secs % 60}초`
  }

  return (
    <>
      <AquaHeader step={3} />

      <div className="result-page">
        <div className={`result-card ${reserved ? 'success' : 'failure'}`}>
          <div className="result-icon">{reserved ? '🎉' : '😢'}</div>
          <h1 className="result-title">
            {reserved ? '수강신청 성공!' : '수강신청 실패'}
          </h1>
          <p className="result-subtitle">
            {reserved
              ? `축하합니다! ${myPosition != null ? `${myPosition}번째로 ` : ''}예약이 완료되었습니다.`
              : '아쉽게도 수강신청에 실패했습니다.'}
          </p>

          <div className="enrollment-details">
            <h3 className="detail-title">📋 결과 내역</h3>
            {courseName && (
              <div className="detail-item">
                <span className="detail-label">강좌명</span>
                <span className="detail-value highlight">{courseName}</span>
              </div>
            )}
            <div className="detail-item">
              <span className="detail-label">소요 시간</span>
              <span className="detail-value highlight">⏱ {formatElapsed(elapsedSeconds)}</span>
            </div>
            {totalParticipants > 0 && (
              <>
                <div className="detail-item">
                  <span className="detail-label">총 참가자</span>
                  <span className="detail-value">{totalParticipants}명</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">예약 성공</span>
                  <span className="detail-value success-color">{successCount}명</span>
                </div>
                <div className="detail-item">
                  <span className="detail-label">예약 실패</span>
                  <span className="detail-value fail-color">{failCount}명</span>
                </div>
              </>
            )}
            {reserved && myPosition != null && (
              <div className="detail-item">
                <span className="detail-label">내 순위</span>
                <span className="detail-value highlight">🏆 {myPosition}위</span>
              </div>
            )}
          </div>

          {!reserved && (
            <div className="failure-reason-box">
              <div className="failure-reason-title">❌ 실패 사유</div>
              <div className="failure-reason-text">
                {failReason || '수강 인원이 마감되었습니다.'}
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
