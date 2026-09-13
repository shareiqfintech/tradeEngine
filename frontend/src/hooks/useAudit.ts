import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { auditApi } from '@/api'
import { queryKeys } from './queryKeys'
import type { AuditFilters } from '@/types'

const AUDIT_POLL_MS = 10_000

export function useAuditEvents(filters: AuditFilters) {
  return useQuery({
    queryKey: queryKeys.audit(filters),
    queryFn: () => auditApi.list(filters),
    refetchInterval: AUDIT_POLL_MS,
    placeholderData: keepPreviousData,
  })
}
