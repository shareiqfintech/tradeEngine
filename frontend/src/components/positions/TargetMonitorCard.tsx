import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { parseOptionSymbol } from '@/lib/optionSymbol'
import { formatCurrency, formatNumber } from '@/lib/format'
import type { PositionTarget, TargetStatus } from '@/types'

const STATUS_META: Record<TargetStatus, { label: string; variant: 'success' | 'info' | 'warning' | 'destructive' | 'muted' }> = {
  PENDING_ENTRY: { label: 'AWAITING FILL', variant: 'muted' },
  MONITORING: { label: 'MONITORING', variant: 'info' },
  TARGET_HIT: { label: 'TARGET HIT', variant: 'warning' },
  CLOSING: { label: 'EXIT SUBMITTED', variant: 'warning' },
  CLOSED: { label: 'CLOSED', variant: 'success' },
  EXIT_FAILED: { label: 'EXIT FAILED', variant: 'destructive' },
}

export function TargetMonitorCard({ target }: { target: PositionTarget }) {
  const parsed = parseOptionSymbol(target.tradingSymbol)
  const meta = STATUS_META[target.targetStatus]

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-start justify-between gap-2">
          <div>
            <div className="text-sm font-semibold">
              {parsed ? (
                <>
                  {parsed.underlying} {formatNumber(parsed.strike)}{' '}
                  <span className={parsed.optionType === 'CE' ? 'text-success' : 'text-destructive'}>{parsed.optionType}</span>
                </>
              ) : (
                target.tradingSymbol
              )}
            </div>
            <div className="mt-0.5 text-xs text-muted-foreground">{target.tradingSymbol}</div>
          </div>
          <Badge variant={meta.variant}>{meta.label}</Badge>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
          <div>
            <div className="text-xs text-muted-foreground">Quantity</div>
            <div className="font-mono font-medium tabular-nums">{formatNumber(target.quantity)}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Entry Price (Groww fill)</div>
            <div className="font-mono font-medium tabular-nums">
              {target.entryPrice != null ? formatCurrency(target.entryPrice) : '—'}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Target Points</div>
            <div className="font-mono font-medium tabular-nums">{formatNumber(target.targetPoints)}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Target Price</div>
            <div className="font-mono font-semibold tabular-nums">
              {target.targetPrice != null ? formatCurrency(target.targetPrice) : 'Calculated after entry'}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Current LTP</div>
            <div className="font-mono font-medium tabular-nums">
              {target.currentLtp != null ? formatCurrency(target.currentLtp) : '—'}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Exit Order</div>
            <div className="font-mono text-xs tabular-nums text-muted-foreground">
              {target.exitOrderReferenceId ?? '—'}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
