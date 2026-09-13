import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'

type Tone = 'default' | 'success' | 'warning' | 'destructive' | 'info'

const ICON_TONE_CLASS: Record<Tone, string> = {
  default: 'text-muted-foreground bg-muted',
  success: 'text-success bg-success-muted',
  warning: 'text-warning bg-warning-muted',
  destructive: 'text-destructive bg-destructive-muted',
  info: 'text-info bg-info-muted',
}

export function StatusCard({
  label,
  value,
  description,
  icon: Icon,
  tone = 'default',
  valueClassName,
}: {
  label: string
  value: ReactNode
  description?: ReactNode
  icon?: LucideIcon
  tone?: Tone
  valueClassName?: string
}) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle>{label}</CardTitle>
        {Icon && (
          <div className={cn('flex size-7 items-center justify-center rounded-md', ICON_TONE_CLASS[tone])}>
            <Icon className="size-4" />
          </div>
        )}
      </CardHeader>
      <CardContent>
        <div className={cn('text-2xl font-semibold tracking-tight tabular-nums', valueClassName)}>{value}</div>
        {description && <div className="mt-1 text-xs text-muted-foreground">{description}</div>}
      </CardContent>
    </Card>
  )
}
