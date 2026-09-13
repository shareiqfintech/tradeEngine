import { formatCurrency, formatNumber } from '@/lib/format'
import { RiskProgress } from './RiskProgress'
import type { RiskStatusResponse } from '@/types'

export function RiskPanel({ risk }: { risk: RiskStatusResponse }) {
  return (
    <div className="space-y-4">
      <RiskProgress label="Daily Loss Limit" used={risk.dailyLossSoFar} limit={risk.maxDailyLoss} format={formatCurrency} />
      <RiskProgress label="Orders Today" used={risk.ordersToday} limit={risk.maxOrdersPerDay} format={formatNumber} />
      <RiskProgress label="Open Positions" used={risk.openPositions} limit={risk.maxOpenPositions} format={formatNumber} />
      {risk.quantityUsedToday !== null && (
        <RiskProgress label="Quantity" used={risk.quantityUsedToday} limit={risk.maxQuantity} format={formatNumber} />
      )}
    </div>
  )
}
