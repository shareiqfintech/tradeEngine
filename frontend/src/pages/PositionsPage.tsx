import { Layers, Target } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusCard } from '@/components/shared/StatusCard'
import { PositionCardSkeleton, StatCardSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { PositionCard } from '@/components/positions/PositionCard'
import { TargetMonitorCard } from '@/components/positions/TargetMonitorCard'
import { usePositions } from '@/hooks/usePositions'
import { usePositionTargets } from '@/hooks/usePositionTargets'
import { formatSignedCurrency } from '@/lib/format'

export function PositionsPage() {
  const { data: positions, isLoading, isError, error, refetch } = usePositions()
  const { data: positionTargets } = usePositionTargets()

  const openPositions = positions ?? []
  const targets = positionTargets ?? []
  const totalUnrealized = openPositions.reduce((sum, p) => sum + (p.unrealizedPnl ?? 0), 0)
  const anyUnrealizedKnown = openPositions.some((p) => p.unrealizedPnl != null)

  return (
    <div>
      <PageHeader title="Positions" description="Live broker positions, retrieved directly from the backend's Groww integration." />

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        {isLoading ? (
          <>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </>
        ) : (
          <>
            <StatusCard label="Open Positions" value={openPositions.length} />
            <StatusCard
              label="Total Unrealized P&L"
              value={anyUnrealizedKnown ? formatSignedCurrency(totalUnrealized) : '—'}
              tone={!anyUnrealizedKnown ? 'default' : totalUnrealized >= 0 ? 'success' : 'destructive'}
            />
            <StatusCard label="Total Realized P&L" value="—" description="Not yet exposed by the backend" />
          </>
        )}
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <PositionCardSkeleton />
          <PositionCardSkeleton />
          <PositionCardSkeleton />
        </div>
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : openPositions.length === 0 ? (
        <EmptyState
          icon={Layers}
          title="No open positions."
          description="Positions opened by executed BUY orders will appear here, sourced live from Groww."
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {openPositions.map((position) => (
            <PositionCard key={position.tradingSymbol} position={position} />
          ))}
        </div>
      )}

      <div className="mt-10">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <Target className="size-4" />
          Target Monitor
        </h2>
        <p className="mb-4 mt-0.5 text-sm text-muted-foreground">
          Automatic profit-target exits. Target Price = actual Groww entry fill + Target Points, watched by the backend
          and closed with a single SELL when the option LTP reaches it.
        </p>
        {targets.length === 0 ? (
          <EmptyState
            icon={Target}
            title="No target-monitored positions."
            description="When a BUY executes with Target Points configured, its automatic profit target appears here."
          />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {targets.map((target) => (
              <TargetMonitorCard key={target.exitOrderReferenceId ?? target.tradingSymbol + target.updatedAt} target={target} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
