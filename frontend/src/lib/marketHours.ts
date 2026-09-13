/**
 * Client-side-only mirror of the backend's MarketHoursService, used ONLY to
 * render a "market status" badge without waiting on a network round trip.
 * This is purely a display convenience - it is NEVER used to gate any
 * action, and the authoritative signal for whether trading is actually
 * allowed is always `tradingEnabled` from GET /api/trading/status.
 */
export function isWithinMarketHoursNowIST(): boolean {
  const now = new Date()
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Kolkata',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(now)

  const weekday = parts.find((p) => p.type === 'weekday')?.value ?? ''
  const hour = Number(parts.find((p) => p.type === 'hour')?.value ?? '0')
  const minute = Number(parts.find((p) => p.type === 'minute')?.value ?? '0')

  if (weekday === 'Sat' || weekday === 'Sun') return false

  const minutesNow = hour * 60 + minute
  const start = 9 * 60 + 15
  const end = 15 * 60 + 30
  return minutesNow >= start && minutesNow <= end
}
