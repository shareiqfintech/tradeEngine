import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/shared/EmptyState'
import { ErrorState } from '@/components/shared/ErrorState'
import { ChartSkeleton } from '@/components/shared/skeletons'
import { usePnlIntraday } from '@/hooks/usePnl'
import { formatCurrency, formatTime } from '@/lib/format'
import { TrendingUp } from 'lucide-react'

export function PnlChart() {
  const { data, isLoading, isError, error, refetch } = usePnlIntraday()

  if (isLoading) return <ChartSkeleton />

  if (isError) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Intraday P&amp;L</CardTitle>
        </CardHeader>
        <CardContent>
          <ErrorState error={error} onRetry={() => void refetch()} />
        </CardContent>
      </Card>
    )
  }

  const points = data ?? []

  return (
    <Card>
      <CardHeader>
        <CardTitle>Intraday P&amp;L</CardTitle>
      </CardHeader>
      <CardContent>
        {points.length === 0 ? (
          <EmptyState
            icon={TrendingUp}
            title="No P&L data yet"
            description="The intraday P&L series will appear here once trades have been executed today."
          />
        ) : (
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={points} margin={{ left: 8, right: 8, top: 8, bottom: 0 }}>
                <defs>
                  <linearGradient id="pnlGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-chart-1)" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="var(--color-chart-1)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
                <XAxis
                  dataKey="time"
                  tickFormatter={(value: string) => formatTime(value)}
                  stroke="var(--color-muted-foreground)"
                  fontSize={11}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  tickFormatter={(value: number) => formatCurrency(value)}
                  stroke="var(--color-muted-foreground)"
                  fontSize={11}
                  tickLine={false}
                  axisLine={false}
                  width={80}
                />
                <Tooltip
                  contentStyle={{
                    background: 'var(--color-popover)',
                    border: '1px solid var(--color-border)',
                    borderRadius: 8,
                    fontSize: 12,
                  }}
                  labelFormatter={(value) => formatTime(String(value))}
                  formatter={(value) => [formatCurrency(Number(value)), 'P&L']}
                />
                <Area type="monotone" dataKey="pnl" stroke="var(--color-chart-1)" fill="url(#pnlGradient)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
