import { useEffect, useState } from 'react'

const IST_FORMATTER = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
})

const IST_DATE_FORMATTER = new Intl.DateTimeFormat('en-IN', {
  timeZone: 'Asia/Kolkata',
  weekday: 'short',
  day: '2-digit',
  month: 'short',
})

/** Purely client-side clock (no backend call) rendering the current India Standard Time in the top bar. */
export function useIndiaClock() {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const interval = setInterval(() => setNow(new Date()), 1_000)
    return () => clearInterval(interval)
  }, [])

  return {
    time: IST_FORMATTER.format(now),
    date: IST_DATE_FORMATTER.format(now),
    raw: now,
  }
}
