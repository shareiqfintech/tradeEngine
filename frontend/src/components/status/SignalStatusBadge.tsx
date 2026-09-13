import { Badge, type BadgeProps } from '@/components/ui/badge'
import type { SignalStatus } from '@/types'

const VARIANT_BY_STATUS: Record<SignalStatus, NonNullable<BadgeProps['variant']>> = {
  RECEIVED: 'info',
  VALIDATED: 'info',
  PROCESSING: 'warning',
  EXECUTED: 'success',
  REJECTED: 'destructive',
  DUPLICATE: 'muted',
  FAILED: 'destructive',
}

export function SignalStatusBadge({ status }: { status: SignalStatus }) {
  return <Badge variant={VARIANT_BY_STATUS[status]}>{status}</Badge>
}
