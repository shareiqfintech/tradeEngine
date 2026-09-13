import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ActionBadge } from '@/components/status/ActionBadge'
import { OrderStatusBadge } from '@/components/status/OrderStatusBadge'
import { EmptyState } from '@/components/shared/EmptyState'
import { formatCurrency, formatDateTime, formatNumber } from '@/lib/format'
import { ReceiptText } from 'lucide-react'
import type { TradingOrder } from '@/types'

export function OrderTable({
  orders,
  onRowClick,
  compact = false,
}: {
  orders: TradingOrder[]
  onRowClick?: (order: TradingOrder) => void
  compact?: boolean
}) {
  if (orders.length === 0) {
    return (
      <EmptyState
        icon={ReceiptText}
        title="No orders yet."
        description="Orders placed by the trading engine (paper or live) will appear here."
      />
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Order Reference</TableHead>
          {!compact && <TableHead>Groww Order ID</TableHead>}
          {!compact && <TableHead>Signal ID</TableHead>}
          <TableHead>Trading Symbol</TableHead>
          <TableHead>Action</TableHead>
          <TableHead className="text-right">Qty</TableHead>
          {!compact && <TableHead>Type</TableHead>}
          {!compact && <TableHead>Product</TableHead>}
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Avg Fill</TableHead>
          <TableHead>Created</TableHead>
          {!compact && <TableHead>Updated</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {orders.map((order) => (
          <TableRow key={order.id} className={onRowClick ? 'cursor-pointer' : undefined} onClick={() => onRowClick?.(order)}>
            <TableCell className="font-mono text-xs font-medium">{order.orderReferenceId}</TableCell>
            {!compact && <TableCell className="font-mono text-xs text-muted-foreground">{order.growwOrderId ?? '—'}</TableCell>}
            {!compact && <TableCell className="font-mono text-xs text-muted-foreground">{order.signalId}</TableCell>}
            <TableCell className="font-medium">{order.tradingSymbol}</TableCell>
            <TableCell>
              <ActionBadge action={order.action} />
            </TableCell>
            <TableCell className="text-right font-mono tabular-nums">{formatNumber(order.quantity)}</TableCell>
            {!compact && <TableCell className="text-muted-foreground">{order.orderType}</TableCell>}
            {!compact && <TableCell className="text-muted-foreground">{order.product}</TableCell>}
            <TableCell>
              <OrderStatusBadge status={order.status} />
            </TableCell>
            <TableCell className="text-right font-mono tabular-nums">{formatCurrency(order.averageFillPrice)}</TableCell>
            <TableCell className="font-mono text-xs text-muted-foreground">{formatDateTime(order.createdAt)}</TableCell>
            {!compact && <TableCell className="font-mono text-xs text-muted-foreground">{formatDateTime(order.updatedAt)}</TableCell>}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
