import type { ReactNode } from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { TableSkeleton, TimelineSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { RiskPanel } from '@/components/risk/RiskPanel'
import { AuditEventList } from '@/components/status/AuditEventList'
import { useRiskRejectionHistory, useRiskStatus } from '@/hooks/useRisk'
import { ShieldAlert } from 'lucide-react'

export function RiskPage() {
  const riskQuery = useRiskStatus()
  const rejectionsQuery = useRiskRejectionHistory()

  return (
    <div>
      <PageHeader title="Risk" description="Live risk limits and usage, enforced entirely by the backend's RiskManagementService." />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Risk Usage</CardTitle>
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

        <Card>
          <CardHeader>
            <CardTitle>Risk Configuration</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            {riskQuery.data ? (
              <>
                <ConfigRow label="Max Orders / Signal" value={riskQuery.data.maxOrdersPerSignal} />
                <ConfigRow
                  label="Allow Short Selling"
                  value={<Badge variant={riskQuery.data.allowShortSelling ? 'warning' : 'muted'}>{riskQuery.data.allowShortSelling ? 'ON' : 'OFF'}</Badge>}
                />
              </>
            ) : (
              <p className="text-xs text-muted-foreground">Unavailable until GET /api/trading/risk is implemented.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Risk Rejection History</CardTitle>
        </CardHeader>
        <CardContent>
          {rejectionsQuery.isLoading ? (
            <TimelineSkeleton />
          ) : rejectionsQuery.isError ? (
            <ErrorState error={rejectionsQuery.error} onRetry={() => void rejectionsQuery.refetch()} />
          ) : (rejectionsQuery.data?.content.length ?? 0) === 0 ? (
            <EmptyState icon={ShieldAlert} title="No risk rejections recorded." description="Signals rejected by the risk engine (e.g. daily loss limit, max open positions) will appear here." />
          ) : (
            <AuditEventList events={rejectionsQuery.data?.content ?? []} showContext />
          )}
        </CardContent>
      </Card>
    </div>
  )
}

function ConfigRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value}</span>
    </div>
  )
}
