import { useState } from 'react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { TimelineSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { Pagination } from '@/components/shared/Pagination'
import { ActivityTimeline } from '@/components/activity/ActivityTimeline'
import { useAuditEvents } from '@/hooks/useAudit'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { AUDIT_EVENT_TYPES } from '@/types'
import type { AuditEventType } from '@/types'

const PAGE_SIZE = 30

export function ActivityPage() {
  const [eventType, setEventType] = useState<AuditEventType | 'ALL'>('ALL')
  const [signalId, setSignalId] = useState('')
  const [page, setPage] = useState(0)

  const debouncedSignalId = useDebouncedValue(signalId)

  const query = useAuditEvents({
    eventType: eventType === 'ALL' ? undefined : eventType,
    signalId: debouncedSignalId || undefined,
    page,
    size: PAGE_SIZE,
  })

  return (
    <div>
      <PageHeader title="Activity" description="Full audit trail of every significant event recorded by the trading engine." />

      <Card>
        <CardContent className="space-y-4 p-4">
          <div className="flex flex-wrap gap-3">
            <Input
              placeholder="Filter by signal ID…"
              value={signalId}
              onChange={(e) => { setSignalId(e.target.value); setPage(0) }}
              className="max-w-56"
            />
            <Select value={eventType} onValueChange={(v) => { setEventType(v as AuditEventType | 'ALL'); setPage(0) }}>
              <SelectTrigger className="w-56"><SelectValue placeholder="Event type" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">All event types</SelectItem>
                {AUDIT_EVENT_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          {query.isLoading ? (
            <TimelineSkeleton items={8} />
          ) : query.isError ? (
            <ErrorState error={query.error} onRetry={() => void query.refetch()} />
          ) : (
            <>
              <ActivityTimeline events={query.data?.content ?? []} />
              <Pagination page={page} totalPages={query.data?.totalPages ?? 0} onPageChange={setPage} />
            </>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
