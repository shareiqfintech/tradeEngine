import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { orderApi } from '@/api'
import { queryKeys } from './queryKeys'
import type { OrderFilters } from '@/types'

const ORDERS_POLL_MS = 5_000

export function useOrders(filters: OrderFilters) {
  return useQuery({
    queryKey: queryKeys.orders(filters),
    queryFn: () => orderApi.list(filters),
    refetchInterval: ORDERS_POLL_MS,
    placeholderData: keepPreviousData,
  })
}

export function useOrder(id: number | string | undefined) {
  return useQuery({
    queryKey: queryKeys.order(id ?? ''),
    queryFn: () => orderApi.getById(id as number | string),
    enabled: id !== undefined,
  })
}
