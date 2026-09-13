import { useState } from 'react'
import { Search } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { TableSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { Pagination } from '@/components/shared/Pagination'
import { SignalTable } from '@/components/signals/SignalTable'
import { SignalDetailDrawer } from '@/components/signals/SignalDetailDrawer'
import { useSignals } from '@/hooks/useSignals'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { SIGNAL_STATUSES, TRADING_ACTIONS } from '@/types'
import type { SignalStatus, TradingAction } from '@/types'

const PAGE_SIZE = 20

export function SignalsPage() {
  const [search, setSearch] = useState('')
  const [action, setAction] = useState<TradingAction | 'ALL'>('ALL')
  const [status, setStatus] = useState<SignalStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(0)
  const [selectedSignalId, setSelectedSignalId] = useState<string | null>(null)

  const debouncedSearch = useDebouncedValue(search)

  const query = useSignals({
    search: debouncedSearch || undefined,
    action: action === 'ALL' ? undefined : action,
    status: status === 'ALL' ? undefined : status,
    page,
    size: PAGE_SIZE,
  })

  return (
    <div>
      <PageHeader title="Signals" description="TradingView webhook alerts received by the trading engine." />

      <Card>
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative min-w-56 flex-1">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search signal ID or underlying…"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
                className="pl-8"
              />
            </div>

            <Select
              value={action}
              onValueChange={(v) => {
                setAction(v as TradingAction | 'ALL')
                setPage(0)
              }}
            >
              <SelectTrigger className="w-36"><SelectValue placeholder="Action" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All actions</SelectItem>
                {TRADING_ACTIONS.map((a) => (
                  <SelectItem key={a} value={a}>{a}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Select
              value={status}
              onValueChange={(v) => {
                setStatus(v as SignalStatus | 'ALL')
                setPage(0)
              }}
            >
              <SelectTrigger className="w-44"><SelectValue placeholder="Status" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All statuses</SelectItem>
                {SIGNAL_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>{s}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {query.isLoading ? (
            <TableSkeleton rows={8} cols={8} />
          ) : query.isError ? (
            <ErrorState error={query.error} onRetry={() => void query.refetch()} />
          ) : (
            <>
              <SignalTable signals={query.data?.content ?? []} onRowClick={(signal) => setSelectedSignalId(signal.signalId)} />
              <Pagination page={page} totalPages={query.data?.totalPages ?? 0} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <SignalDetailDrawer signalId={selectedSignalId} onOpenChange={(open) => !open && setSelectedSignalId(null)} />
    </div>
  )
}
