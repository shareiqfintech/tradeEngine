import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { parseOptionSymbol } from '@/lib/optionSymbol'
import { cn } from '@/lib/utils'
import { formatCurrency, formatNumber, formatSignedCurrency } from '@/lib/format'
import type { TradingPosition } from '@/types'

export function PositionCard({ position }: { position: TradingPosition }) {
  const parsed = parseOptionSymbol(position.tradingSymbol)
  const pnl = position.unrealizedPnl ?? null
  const pnlTone = pnl === null ? 'text-muted-foreground' : pnl >= 0 ? 'text-success' : 'text-destructive'

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
                position.tradingSymbol
              )}
            </div>
            <div className="mt-0.5 text-xs text-muted-foreground">{position.tradingSymbol}</div>
          </div>
          <Badge variant={position.netQuantity >= 0 ? 'success' : 'destructive'}>
            {position.netQuantity >= 0 ? 'LONG' : 'SHORT'}
          </Badge>
        </div>

        <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
          <div>
            <div className="text-xs text-muted-foreground">Quantity</div>
            <div className="font-mono font-medium tabular-nums">{formatNumber(Math.abs(position.netQuantity))}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Avg Price</div>
            <div className="font-mono font-medium tabular-nums">{formatCurrency(position.averagePrice)}</div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">LTP</div>
            <div className="font-mono font-medium tabular-nums">
              {position.ltp != null ? formatCurrency(position.ltp) : '—'}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">Unrealized P&amp;L</div>
            <div className={cn('font-mono font-semibold tabular-nums', pnlTone)}>
              {pnl !== null ? formatSignedCurrency(pnl) : '—'}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
