import './PoolBackground.css'

const LANE_COUNT = 6

const SWIMMERS = [
  { lane: 0, dur: 9,   del: 0,    cap: '#e53935' },
  { lane: 1, dur: 13,  del: -6.5, cap: '#1e88e5' },
  { lane: 2, dur: 10,  del: -2,   cap: '#43a047' },
  { lane: 3, dur: 11,  del: -7,   cap: '#fb8c00' },
  { lane: 4, dur: 8,   del: -1.5, cap: '#8e24aa' },
  { lane: 5, dur: 12,  del: -5,   cap: '#e91e63' },
]

const CAUSTICS = [
  { x: 5,  y: 10, w: 120, h: 50,  dur: 4,   del: 0    },
  { x: 25, y: 62, w: 80,  h: 35,  dur: 5,   del: -1   },
  { x: 45, y: 28, w: 140, h: 58,  dur: 3.5, del: -2   },
  { x: 65, y: 75, w: 90,  h: 38,  dur: 4.5, del: -0.5 },
  { x: 80, y: 18, w: 110, h: 46,  dur: 6,   del: -3   },
  { x: 15, y: 85, w: 70,  h: 30,  dur: 3,   del: -1.5 },
  { x: 55, y: 50, w: 130, h: 54,  dur: 5.5, del: -2.5 },
  { x: 90, y: 65, w: 95,  h: 40,  dur: 4,   del: -4   },
  { x: 35, y: 42, w: 60,  h: 28,  dur: 3.8, del: -0.8 },
  { x: 72, y: 38, w: 100, h: 44,  dur: 4.2, del: -3.5 },
]

const laneHeight = 100 / LANE_COUNT

export default function PoolBackground() {
  return (
    <div className="pool-bg" aria-hidden="true">
      <div className="pool-texture" />

      {CAUSTICS.map((c, i) => (
        <div
          key={i}
          className="caustic"
          style={{
            left: `${c.x}%`,
            top: `${c.y}%`,
            width: c.w,
            height: c.h,
            animationDuration: `${c.dur}s`,
            animationDelay: `${c.del}s`,
          }}
        />
      ))}

      {Array.from({ length: LANE_COUNT + 1 }, (_, i) => (
        <div
          key={i}
          className="lane-rope"
          style={{ top: `${i * laneHeight}%` }}
        >
          {Array.from({ length: 36 }, (_, j) => (
            <span key={j} className={`rope-float ${j % 2 === 0 ? 'rf-a' : 'rf-b'}`} />
          ))}
        </div>
      ))}

      {/* T-lines at center of each lane */}
      {Array.from({ length: LANE_COUNT }, (_, i) => (
        <div
          key={`tl-${i}`}
          className="t-line"
          style={{ top: `${(i + 0.5) * laneHeight}%` }}
        />
      ))}

      {SWIMMERS.map(({ lane, dur, del, cap }) => (
        <div
          key={lane}
          className="swimmer-track"
          style={{
            top: `${(lane + 0.5) * laneHeight}%`,
            animationDuration: `${dur}s`,
            animationDelay: `${del}s`,
          }}
        >
          <div className="swimmer">
            <div className="swim-wake" />
            <div className="swim-body" />
            <div className="swim-cap" style={{ background: cap }} />
          </div>
        </div>
      ))}
    </div>
  )
}
