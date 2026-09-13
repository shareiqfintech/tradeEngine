import { AuditEventList } from '@/components/status/AuditEventList'
import { EmptyState } from '@/components/shared/EmptyState'
import { ListTree } from 'lucide-react'
import type { AuditEvent } from '@/types'

/**
 * Per-signal execution timeline for the signal detail drawer. Renders the
 * REAL, chronological audit trail for one signalId - it does not fabricate
 * a fixed set of pipeline stages; whatever the backend actually recorded
 * (or didn't) is exactly what's shown.
 */
export function ExecutionTimeline({ events }: { events: AuditEvent[] }) {
  if (events.length === 0) {
    return (
      <EmptyState
        icon={ListTree}
        title="No audit trail recorded"
        description="No audit_event rows exist for this signal yet."
      />
    )
  }
  return <AuditEventList events={events} />
}
