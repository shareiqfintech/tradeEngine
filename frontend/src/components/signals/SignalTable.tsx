import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { ActionBadge } from '@/components/status/ActionBadge'
import { SignalStatusBadge } from '@/components/status/SignalStatusBadge'
import { EmptyState } from '@/components/shared/EmptyState'
import { formatCurrency, formatDateTime } from '@/lib/format'
import { Radio } from 'lucide-react'
import type { TradingSignal } from '@/types'

export function SignalTable({
  signals,
  onRowClick,
  compact = false,
}: {
  signals: TradingSignal[]
  onRowClick?: (signal: TradingSignal) => void
  compact?: boolean
}) {
  if (signals.length === 0) {
    return (
      <EmptyState
        icon={Radio}
        title="No TradingView signals received yet."
        description="Signals will appear here as soon as TradingView sends a webhook alert to the trading engine."
      />
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Time</TableHead>
          <TableHead>Signal ID</TableHead>
          <TableHead>Action</TableHead>
          <TableHead>Underlying</TableHead>
          {!compact && <TableHead>Exchange</TableHead>}
          {!compact && <TableHead>Timeframe</TableHead>}
          <TableHead className="text-right">Signal Price</TableHead>
          <TableHead>Status</TableHead>
          {!compact && <TableHead>Rejection Reason</TableHead>}
        </TableRow>
      </TableHeader>
      <TableBody>
        {signals.map((signal) => (
          <TableRow
            key={signal.id}
            className={onRowClick ? 'cursor-pointer' : undefined}
            onClick={() => onRowClick?.(signal)}
          >
            <TableCell className="font-mono text-xs text-muted-foreground">{formatDateTime(signal.createdAt)}</TableCell>
            <TableCell className="font-mono text-xs font-medium">{signal.signalId}</TableCell>
            <TableCell>
              <ActionBadge action={signal.action} />
            </TableCell>
            <TableCell className="font-medium">{signal.underlying}</TableCell>
            {!compact && <TableCell className="text-muted-foreground">{signal.exchange}</TableCell>}
            {!compact && <TableCell className="text-muted-foreground">{signal.timeframe}</TableCell>}
            <TableCell className="text-right font-mono tabular-nums">{formatCurrency(signal.price)}</TableCell>
            <TableCell>
              <SignalStatusBadge status={signal.status} />
            </TableCell>
            {!compact && (
              <TableCell className="max-w-64 truncate text-xs text-muted-foreground" title={signal.rejectionReason ?? undefined}>
                {signal.rejectionReason ?? '—'}
              </TableCell>
            )}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
