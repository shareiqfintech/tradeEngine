import { cn } from '@/lib/utils'
import { formatDateTime, formatRelativeTime } from '@/lib/format'
import { AUDIT_EVENT_META, AUDIT_TONE_CLASS, UNKNOWN_AUDIT_EVENT_META } from './auditEventMeta'
import type { AuditEvent } from '@/types'

/**
 * Shared chronological renderer for a list of REAL AuditEventEntity rows.
 * Used both as the per-signal "execution timeline" (ExecutionTimeline) and
 * the system-wide Activity feed (ActivityTimeline) - the underlying data
 * and step semantics are identical, only the scope of the query differs.
 */
export function AuditEventList({
  events,
  showContext = false,
}: {
  events: AuditEvent[]
  showContext?: boolean
}) {
  return (
    <ol className="space-y-4">
      {events.map((event, index) => {
        const meta = AUDIT_EVENT_META[event.eventType] ?? {
          ...UNKNOWN_AUDIT_EVENT_META,
          label: event.eventType ?? UNKNOWN_AUDIT_EVENT_META.label,
        }
        const Icon = meta.icon
        const isLast = index === events.length - 1
        return (
          <li key={event.id} className="relative flex gap-3">
            {!isLast && <span className="absolute left-4 top-9 h-[calc(100%-2px)] w-px bg-border" aria-hidden />}
            <span className={cn('flex size-8 shrink-0 items-center justify-center rounded-full', AUDIT_TONE_CLASS[meta.tone])}>
              <Icon className="size-4" />
            </span>
            <div className="flex-1 pb-1 pt-0.5">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="text-sm font-medium">{meta.label}</p>
                <time className="text-xs text-muted-foreground" dateTime={event.createdAt} title={formatDateTime(event.createdAt)}>
                  {formatRelativeTime(event.createdAt)}
                </time>
              </div>
              {event.details && <p className="mt-0.5 text-xs text-muted-foreground">{event.details}</p>}
              {showContext && (event.signalId || event.orderReferenceId) && (
                <p className="mt-1 font-mono text-[11px] text-muted-foreground/70">
                  {event.signalId && <>signal: {event.signalId} </>}
                  {event.orderReferenceId && <>order: {event.orderReferenceId}</>}
                </p>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
