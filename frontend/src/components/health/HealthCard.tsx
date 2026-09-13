import { Card, CardContent } from '@/components/ui/card'
import { ComponentHealthDot } from '@/components/status/ComponentHealthDot'
import { Badge } from '@/components/ui/badge'
import type { LucideIcon } from 'lucide-react'
import type { ComponentHealth } from '@/types'

export function HealthCard({
  name,
  status,
  icon: Icon,
  detail,
  notExposed,
}: {
  name: string
  status: ComponentHealth
  icon: LucideIcon
  detail?: string
  /** True when the backend has no real indicator for this component yet - shown as an honest badge instead of a fabricated status. */
  notExposed?: boolean
}) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-3">
          <div className="flex size-9 items-center justify-center rounded-md bg-muted">
            <Icon className="size-4 text-muted-foreground" />
          </div>
          <div>
            <p className="text-sm font-medium">{name}</p>
            {detail && <p className="text-xs text-muted-foreground">{detail}</p>}
          </div>
        </div>
        {notExposed ? (
          <Badge variant="muted">NOT EXPOSED</Badge>
        ) : (
          <ComponentHealthDot status={status} />
        )}
      </CardContent>
    </Card>
  )
}
