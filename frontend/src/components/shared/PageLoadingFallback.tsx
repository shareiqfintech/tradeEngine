import { StatCardSkeleton, TableSkeleton } from './skeletons'

/** Shown briefly while a lazy-loaded route chunk downloads - not a data-loading state. */
export function PageLoadingFallback() {
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCardSkeleton />
        <StatCardSkeleton />
        <StatCardSkeleton />
        <StatCardSkeleton />
      </div>
      <TableSkeleton />
    </div>
  )
}
