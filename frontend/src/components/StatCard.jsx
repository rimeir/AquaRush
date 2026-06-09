import './StatCard.css'

export default function StatCard({ label, value, icon, highlight }) {
  return (
    <div className={`stat-card ${highlight ? 'highlight' : ''}`}>
      <span className="stat-icon">{icon}</span>
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  )
}
