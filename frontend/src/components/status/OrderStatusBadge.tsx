import { Badge, type BadgeProps } from '@/components/ui/badge'
import type { OrderStatus } from '@/types'

const VARIANT_BY_STATUS: Record<OrderStatus, NonNullable<BadgeProps['variant']>> = {
  PENDING: 'info',
  OPEN: 'warning',
  COMPLETE: 'success',
  REJECTED: 'destructive',
  CANCELLED: 'muted',
  FAILED: 'destructive',
}

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge variant={VARIANT_BY_STATUS[status]}>{status}</Badge>
}
