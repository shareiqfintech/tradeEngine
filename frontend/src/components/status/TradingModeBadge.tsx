import { FlaskConical, Zap } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { TradingMode } from '@/types'

export function TradingModeBadge({ mode, className }: { mode: TradingMode; className?: string }) {
  if (mode === 'LIVE') {
    return (
      <Badge variant="destructive" className={cn('font-semibold', className)}>
        <Zap className="size-3" />
        LIVE
      </Badge>
    )
  }
  return (
    <Badge variant="info" className={cn('font-semibold', className)}>
      <FlaskConical className="size-3" />
      PAPER
    </Badge>
  )
}
