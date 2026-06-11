import { useState, useEffect } from 'react'

const OPEN_TIME = new Date('2025-01-15T09:00:00').getTime()

export function useVirtualClock() {
  const [virtualMs, setVirtualMs] = useState(() => {
    const saved = sessionStorage.getItem('virtualTime')
    const savedReal = sessionStorage.getItem('virtualStartReal')
    if (saved && savedReal) {
      const elapsed = Date.now() - parseInt(savedReal)
      return parseInt(saved) + elapsed
    }
    const start = new Date('2025-01-15T08:59:00').getTime()
    sessionStorage.setItem('virtualTime', start.toString())
    sessionStorage.setItem('virtualStartReal', Date.now().toString())
    return start
  })

  useEffect(() => {
    const id = setInterval(() => setVirtualMs(prev => prev + 1000), 1000)
    return () => clearInterval(id)
  }, [])

  const t = new Date(virtualMs)
  const h = String(t.getHours()).padStart(2, '0')
  const m = String(t.getMinutes()).padStart(2, '0')
  const s = String(t.getSeconds()).padStart(2, '0')

  return {
    time: `${h}:${m}:${s}`,
    isOpen: virtualMs >= OPEN_TIME,
    secondsUntilOpen: Math.max(0, Math.ceil((OPEN_TIME - virtualMs) / 1000)),
    virtualMs,
    openTimeMs: OPEN_TIME,
  }
}
