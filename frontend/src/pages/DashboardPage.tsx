import { Link } from 'react-router-dom'
import { Activity, Gauge, HeartPulse, Plug, ScrollText } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusCard } from '@/components/shared/StatusCard'
import { StatCardSkeleton, TableSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { SignalTable } from '@/components/signals/SignalTable'
import { OrderTable } from '@/components/orders/OrderTable'
import { TradingControls } from '@/components/dashboard/TradingControls'
import { PnlChart } from '@/components/dashboard/PnlChart'
import { RiskPanel } from '@/components/risk/RiskPanel'
import { deriveTradingStatusLabel } from '@/components/dashboard/tradingStatusLabel'
import { useTradingStatus } from '@/hooks/useTradingStatus'
import { useActuatorHealth } from '@/hooks/useHealth'
import { usePnlSummary } from '@/hooks/usePnl'
import { useRiskStatus } from '@/hooks/useRisk'
import { useSignals } from '@/hooks/useSignals'
import { useOrders } from '@/hooks/useOrders'
import { formatSignedCurrency, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'

const STATUS_LABEL_TEXT: Record<ReturnType<typeof deriveTradingStatusLabel>, string> = {
  ENABLED: 'ENABLED',
  PAUSED: 'PAUSED',
  KILL_SWITCH: 'KILL SWITCH',
  AUTH_REQUIRED: 'AUTH REQUIRED',
  SESSION_CLOSING: 'SAFETY EXIT',
  MARKET_CLOSED: 'MARKET CLOSED',
}

export function DashboardPage() {
  const statusQuery = useTradingStatus()
  const healthQuery = useActuatorHealth()
  const pnlQuery = usePnlSummary()
  const riskQuery = useRiskStatus()
  const signalsQuery = useSignals({ size: 5 })
  const ordersQuery = useOrders({ size: 5 })

  const status = statusQuery.data

  return (
    <div>
      <PageHeader title="Dashboard" description="Live overview of the automated F&O trading engine." />

      {/* Top status cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {statusQuery.isLoading ? (
          <>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </>
        ) : statusQuery.isError || !status ? (
          <div className="col-span-full">
            <ErrorState error={statusQuery.error} onRetry={() => void statusQuery.refetch()} />
          </div>
        ) : (
          <>
            <StatusCard label="Trading Mode" value={status.mode} icon={Plug} tone={status.mode === 'LIVE' ? 'destructive' : 'info'} />
            <StatusCard
              label="Trading Status"
              value={STATUS_LABEL_TEXT[deriveTradingStatusLabel(status)]}
              icon={Gauge}
              tone={
                deriveTradingStatusLabel(status) === 'ENABLED'
                  ? 'success'
                  : deriveTradingStatusLabel(status) === 'KILL_SWITCH'
                    ? 'destructive'
                    : 'warning'
              }
            />
            <StatusCard
              label="Groww Connection"
              value={status.growwAuthenticated ? 'AUTHENTICATED' : 'AUTH_REQUIRED'}
              icon={ScrollText}
              tone={status.growwAuthenticated ? 'success' : 'warning'}
            />
            <StatusCard
              label="System Health"
              value={healthQuery.isLoading ? '…' : healthQuery.isError ? 'DOWN' : healthQuery.data?.status === 'UP' ? 'HEALTHY' : 'DEGRADED'}
              icon={HeartPulse}
              tone={healthQuery.isError ? 'destructive' : healthQuery.data?.status === 'UP' ? 'success' : 'warning'}
            />
          </>
        )}
      </div>

      {/* Trading controls */}
      <div className="mt-4">
        <TradingControls />
      </div>

      {/* Performance */}
      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <PnlChart />
        </div>
        <Card>
          <CardHeader>
            <CardTitle>Performance</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-2 gap-4">
            <PerformanceStat label="Today's P&L" value={pnlQuery.data?.todayPnl} unavailable={pnlQuery.isError} />
            <PerformanceStat label="Realized P&L" value={pnlQuery.data?.realizedPnl} unavailable={pnlQuery.isError} />
            <PerformanceStat label="Unrealized P&L" value={pnlQuery.data?.unrealizedPnl} unavailable={pnlQuery.isError} />
            <div>
              <div className="text-xs text-muted-foreground">Orders Today</div>
              <div className="font-mono text-lg font-semibold tabular-nums">{formatNumber(status?.ordersToday)}</div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground">Open Positions</div>
              <div className="font-mono text-lg font-semibold tabular-nums">{formatNumber(status?.openPositions)}</div>
            </div>
            {pnlQuery.data?.winCount != null && pnlQuery.data?.lossCount != null && (
              <div>
                <div className="text-xs text-muted-foreground">Win / Loss</div>
                <div className="font-mono text-lg font-semibold tabular-nums">
                  {pnlQuery.data.winCount} / {pnlQuery.data.lossCount}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Risk overview */}
      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Risk Overview</CardTitle>
        </CardHeader>
        <CardContent>
          {riskQuery.isLoading ? (
            <TableSkeleton rows={4} cols={1} />
          ) : riskQuery.isError || !riskQuery.data ? (
            <ErrorState error={riskQuery.error} onRetry={() => void riskQuery.refetch()} />
          ) : (
            <RiskPanel risk={riskQuery.data} />
          )}
        </CardContent>
      </Card>

      {/* Recent signals + orders */}
      <div className="mt-6 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>Recent Signals</CardTitle>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/signals">View all</Link>
            </Button>
          </CardHeader>
          <CardContent>
            {signalsQuery.isLoading ? (
              <TableSkeleton rows={4} cols={4} />
            ) : signalsQuery.isError ? (
              <ErrorState error={signalsQuery.error} onRetry={() => void signalsQuery.refetch()} />
            ) : (
              <SignalTable signals={signalsQuery.data?.content ?? []} compact />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle>Recent Orders</CardTitle>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/orders">View all</Link>
            </Button>
          </CardHeader>
          <CardContent>
            {ordersQuery.isLoading ? (
              <TableSkeleton rows={4} cols={4} />
            ) : ordersQuery.isError ? (
              <ErrorState error={ordersQuery.error} onRetry={() => void ordersQuery.refetch()} />
            ) : (
              <OrderTable orders={ordersQuery.data?.content ?? []} compact />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function PerformanceStat({ label, value, unavailable }: { label: string; value: number | undefined; unavailable: boolean }) {
  const tone = unavailable || value === undefined ? 'text-muted-foreground' : value >= 0 ? 'text-success' : 'text-destructive'
  return (
    <div>
      <div className="flex items-center gap-1 text-xs text-muted-foreground">
        <Activity className="size-3" />
        {label}
      </div>
      <div className={cn('font-mono text-lg font-semibold tabular-nums', tone)}>
        {unavailable ? '—' : formatSignedCurrency(value)}
      </div>
    </div>
  )
}
