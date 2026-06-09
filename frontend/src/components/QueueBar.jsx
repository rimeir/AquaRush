import './QueueBar.css'

export default function QueueBar({ queueLength, totalParticipants, myRank }) {
  const queueRate = totalParticipants > 0
    ? Math.min((queueLength / totalParticipants) * 100, 100)
    : 0

  const myRankRate = totalParticipants > 0 && myRank
    ? Math.min((myRank / totalParticipants) * 100, 100)
    : null

  return (
    <div className="queue-bar-wrap">
      <div className="queue-bar-track">
        <div className="queue-bar-fill" style={{ width: `${queueRate}%` }} />
        {myRankRate !== null && (
          <div className="queue-my-marker" style={{ left: `${myRankRate}%` }}>
            <span className="marker-label">나</span>
          </div>
        )}
      </div>
    </div>
  )
}
