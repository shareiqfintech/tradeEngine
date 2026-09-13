import type { ReactNode } from 'react'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { ActionBadge } from '@/components/status/ActionBadge'
import { SignalStatusBadge } from '@/components/status/SignalStatusBadge'
import { ErrorState } from '@/components/shared/ErrorState'
import { TimelineSkeleton } from '@/components/shared/skeletons'
import { Separator } from '@/components/ui/separator'
import { ExecutionTimeline } from './ExecutionTimeline'
import { useSignal } from '@/hooks/useSignals'
import { formatCurrency, formatDateTime } from '@/lib/format'

export function SignalDetailDrawer({
  signalId,
  onOpenChange,
}: {
  signalId: string | null
  onOpenChange: (open: boolean) => void
}) {
  const { data: signal, isLoading, isError, error, refetch } = useSignal(signalId ?? undefined)

  return (
    <Sheet open={signalId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle className="font-mono">{signalId}</SheetTitle>
          <SheetDescription>TradingView signal detail and execution timeline</SheetDescription>
        </SheetHeader>

        {isLoading && <TimelineSkeleton />}
        {isError && <ErrorState error={error} onRetry={() => void refetch()} />}

        {signal && (
          <div className="space-y-6">
            <div className="grid grid-cols-2 gap-4 rounded-lg border border-border p-4 text-sm">
              <Field label="Action"><ActionBadge action={signal.action} /></Field>
              <Field label="Status"><SignalStatusBadge status={signal.status} /></Field>
              <Field label="Underlying">{signal.underlying}</Field>
              <Field label="Exchange">{signal.exchange}</Field>
              <Field label="Timeframe">{signal.timeframe}</Field>
              <Field label="Signal Price">{formatCurrency(signal.price)}</Field>
              <Field label="Signal Time">{formatDateTime(signal.signalTimestamp)}</Field>
              <Field label="Received">{formatDateTime(signal.createdAt)}</Field>
              {signal.rejectionReason && (
                <div className="col-span-2">
                  <Field label="Rejection Reason">
                    <span className="text-destructive">{signal.rejectionReason}</span>
                  </Field>
                </div>
              )}
            </div>

            <Separator />

            <div>
              <h3 className="mb-3 text-sm font-semibold">Execution Timeline</h3>
              <ExecutionTimeline events={signal.auditTrail} />
            </div>

            {signal.order && (
              <>
                <Separator />
                <div>
                  <h3 className="mb-2 text-sm font-semibold">Resulting Order</h3>
                  <div className="rounded-lg border border-border p-4 text-sm">
                    <Field label="Order Reference">
                      <span className="font-mono">{signal.order.orderReferenceId}</span>
                    </Field>
                    <Field label="Trading Symbol">{signal.order.tradingSymbol}</Field>
                    <Field label="Status">{signal.order.status}</Field>
                  </div>
                </div>
              </>
            )}
          </div>
        )}
      </SheetContent>
    </Sheet>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-0.5">
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="font-medium">{children}</div>
    </div>
  )
}
