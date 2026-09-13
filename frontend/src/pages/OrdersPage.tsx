import { useState } from 'react'
import { Search } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { TableSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { Pagination } from '@/components/shared/Pagination'
import { OrderTable } from '@/components/orders/OrderTable'
import { OrderDetailDrawer } from '@/components/orders/OrderDetailDrawer'
import { useOrders } from '@/hooks/useOrders'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { ORDER_STATUSES, TRADING_ACTIONS, TRADING_MODES } from '@/types'
import type { OrderStatus, TradingAction, TradingMode } from '@/types'

const PAGE_SIZE = 20

export function OrdersPage() {
  const [search, setSearch] = useState('')
  const [action, setAction] = useState<TradingAction | 'ALL'>('ALL')
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL')
  const [mode, setMode] = useState<TradingMode | 'ALL'>('ALL')
  const [page, setPage] = useState(0)
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null)

  const debouncedSearch = useDebouncedValue(search)

  const query = useOrders({
    tradingSymbol: debouncedSearch || undefined,
    action: action === 'ALL' ? undefined : action,
    status: status === 'ALL' ? undefined : status,
    mode: mode === 'ALL' ? undefined : mode,
    page,
    size: PAGE_SIZE,
  })

  return (
    <div>
      <PageHeader title="Orders" description="Every order the trading engine has placed, in PAPER or LIVE mode." />

      <Card>
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-wrap gap-3">
            <div className="relative min-w-56 flex-1">
              <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search trading symbol…"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
                className="pl-8"
              />
            </div>

            <Select value={action} onValueChange={(v) => { setAction(v as TradingAction | 'ALL'); setPage(0) }}>
              <SelectTrigger className="w-32"><SelectValue placeholder="Action" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All actions</SelectItem>
                {TRADING_ACTIONS.map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}
              </SelectContent>
            </Select>

            <Select value={status} onValueChange={(v) => { setStatus(v as OrderStatus | 'ALL'); setPage(0) }}>
              <SelectTrigger className="w-40"><SelectValue placeholder="Status" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All statuses</SelectItem>
                {ORDER_STATUSES.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}
              </SelectContent>
            </Select>

            <Select value={mode} onValueChange={(v) => { setMode(v as TradingMode | 'ALL'); setPage(0) }}>
              <SelectTrigger className="w-32"><SelectValue placeholder="Mode" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">PAPER + LIVE</SelectItem>
                {TRADING_MODES.map((m) => <SelectItem key={m} value={m}>{m}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          {query.isLoading ? (
            <TableSkeleton rows={8} cols={9} />
          ) : query.isError ? (
            <ErrorState error={query.error} onRetry={() => void query.refetch()} />
          ) : (
            <>
              <OrderTable orders={query.data?.content ?? []} onRowClick={(order) => setSelectedOrderId(order.id)} />
              <Pagination page={page} totalPages={query.data?.totalPages ?? 0} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>

      <OrderDetailDrawer orderId={selectedOrderId} onOpenChange={(open) => !open && setSelectedOrderId(null)} />
    </div>
  )
}
