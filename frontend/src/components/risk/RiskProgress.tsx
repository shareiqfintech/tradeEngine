import { Progress } from '@/components/ui/progress'
import { Badge } from '@/components/ui/badge'
import { formatNumber } from '@/lib/format'
import { RISK_LEVEL_BADGE_VARIANT, RISK_LEVEL_COLOR, riskLevelFor } from './riskLevel'

export function RiskProgress({
  label,
  used,
  limit,
  format = formatNumber,
  showLevel = true,
}: {
  label: string
  used: number
  limit: number
  format?: (value: number) => string
  showLevel?: boolean
}) {
  const level = riskLevelFor(used, limit)
  const percent = limit > 0 ? Math.min(100, (used / limit) * 100) : 0

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium">{label}</span>
        <div className="flex items-center gap-2">
          <span className="font-mono tabular-nums text-muted-foreground">
            {format(used)} / {format(limit)}
          </span>
          {showLevel && <Badge variant={RISK_LEVEL_BADGE_VARIANT[level]}>{level}</Badge>}
        </div>
      </div>
      <Progress value={percent} indicatorClassName={RISK_LEVEL_COLOR[level]} />
    </div>
  )
}
