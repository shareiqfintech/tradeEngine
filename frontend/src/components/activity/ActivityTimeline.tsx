import { AuditEventList } from '@/components/status/AuditEventList'
import { EmptyState } from '@/components/shared/EmptyState'
import { History } from 'lucide-react'
import type { AuditEvent } from '@/types'

export function ActivityTimeline({ events }: { events: AuditEvent[] }) {
  if (events.length === 0) {
    return (
      <EmptyState
        icon={History}
        title="No system activity recorded yet."
        description="Every significant event (authentication, signals, risk checks, orders, kill switch) will appear here as it happens."
      />
    )
  }
  return <AuditEventList events={events} showContext />
}
