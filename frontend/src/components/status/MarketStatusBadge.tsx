import { useEffect, useState } from 'react'
import { Circle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { isWithinMarketHoursNowIST } from '@/lib/marketHours'
import { cn } from '@/lib/utils'

/**
 * Display-only market-open/closed indicator, recomputed client-side every
 * 30s from the current IST clock (see lib/marketHours.ts). Never gates any
 * action - `tradingEnabled` from the status endpoint is authoritative.
 */
export function MarketStatusBadge({ className }: { className?: string }) {
  const [open, setOpen] = useState(() => isWithinMarketHoursNowIST())

  useEffect(() => {
    const interval = setInterval(() => setOpen(isWithinMarketHoursNowIST()), 30_000)
    return () => clearInterval(interval)
  }, [])

  return (
    <Badge variant={open ? 'success' : 'muted'} className={cn('font-medium', className)}>
      <Circle className={cn('size-2 fill-current', open && 'animate-pulse')} />
      {open ? 'Market Open' : 'Market Closed'}
    </Badge>
  )
}
