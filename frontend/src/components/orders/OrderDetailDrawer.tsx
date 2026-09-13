import type { ReactNode } from 'react'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from '@/components/ui/sheet'
import { ActionBadge } from '@/components/status/ActionBadge'
import { OrderStatusBadge } from '@/components/status/OrderStatusBadge'
import { ErrorState } from '@/components/shared/ErrorState'
import { TimelineSkeleton } from '@/components/shared/skeletons'
import { Separator } from '@/components/ui/separator'
import { ExecutionTimeline } from '@/components/signals/ExecutionTimeline'
import { useOrder } from '@/hooks/useOrders'
import { formatCurrency, formatDateTime, formatNumber } from '@/lib/format'

export function OrderDetailDrawer({
  orderId,
  onOpenChange,
}: {
  orderId: number | null
  onOpenChange: (open: boolean) => void
}) {
  const { data: order, isLoading, isError, error, refetch } = useOrder(orderId ?? undefined)

  return (
    <Sheet open={orderId !== null} onOpenChange={onOpenChange}>
      <SheetContent className="overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle className="font-mono">{order?.orderReferenceId ?? 'Order'}</SheetTitle>
          <SheetDescription>Order request, broker response, and fill details</SheetDescription>
        </SheetHeader>

        {isLoading && <TimelineSkeleton />}
        {isError && <ErrorState error={error} onRetry={() => void refetch()} />}

        {order && (
          <div className="space-y-6">
            <section>
              <h3 className="mb-2 text-sm font-semibold">Order Request</h3>
              <div className="grid grid-cols-2 gap-4 rounded-lg border border-border p-4 text-sm">
                <Field label="Signal ID"><span className="font-mono">{order.signalId}</span></Field>
                <Field label="Action"><ActionBadge action={order.action} /></Field>
                <Field label="Underlying">{order.underlying}</Field>
                <Field label="Trading Symbol">{order.tradingSymbol}</Field>
                <Field label="Quantity">{formatNumber(order.quantity)}</Field>
                <Field label="Order Type">{order.orderType}</Field>
                <Field label="Product">{order.product}</Field>
                <Field label="Segment">{order.segment}</Field>
              </div>
            </section>

            <section>
              <h3 className="mb-2 text-sm font-semibold">Broker Response</h3>
              <div className="grid grid-cols-2 gap-4 rounded-lg border border-border p-4 text-sm">
                <Field label="Groww Order ID"><span className="font-mono">{order.growwOrderId ?? '—'}</span></Field>
                <Field label="Status"><OrderStatusBadge status={order.status} /></Field>
                <Field label="Filled Quantity">{formatNumber(order.filledQuantity)}</Field>
                <Field label="Average Fill Price">{formatCurrency(order.averageFillPrice)}</Field>
                {order.brokerRemark && (
                  <div className="col-span-2">
                    <Field label="Broker Remark">{order.brokerRemark}</Field>
                  </div>
                )}
              </div>
            </section>

            <section className="grid grid-cols-2 gap-4 text-sm">
              <Field label="Created">{formatDateTime(order.createdAt)}</Field>
              <Field label="Updated">{formatDateTime(order.updatedAt)}</Field>
            </section>

            <Separator />

            <section>
              <h3 className="mb-3 text-sm font-semibold">Timeline</h3>
              <ExecutionTimeline events={order.auditTrail} />
            </section>
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
