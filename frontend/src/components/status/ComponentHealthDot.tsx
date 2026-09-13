import { cn } from '@/lib/utils'
import type { ComponentHealth } from '@/types'

const COLOR_BY_STATUS: Record<ComponentHealth, string> = {
  HEALTHY: 'bg-success',
  DEGRADED: 'bg-warning',
  DOWN: 'bg-destructive',
  UNKNOWN: 'bg-muted-foreground',
}

const LABEL_BY_STATUS: Record<ComponentHealth, string> = {
  HEALTHY: 'Healthy',
  DEGRADED: 'Degraded',
  DOWN: 'Down',
  UNKNOWN: 'Unknown',
}

export function ComponentHealthDot({ status, className }: { status: ComponentHealth; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2 text-sm font-medium', className)}>
      <span className={cn('size-2 rounded-full', COLOR_BY_STATUS[status], status === 'HEALTHY' && 'animate-pulse')} />
      {LABEL_BY_STATUS[status]}
    </span>
  )
}
