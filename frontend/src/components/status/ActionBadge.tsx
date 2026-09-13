import { ArrowDownRight, ArrowUpRight } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import type { TradingAction } from '@/types'

export function ActionBadge({ action }: { action: TradingAction }) {
  if (action === 'BUY') {
    return (
      <Badge variant="success">
        <ArrowUpRight className="size-3" />
        BUY
      </Badge>
    )
  }
  return (
    <Badge variant="destructive">
      <ArrowDownRight className="size-3" />
      SELL
    </Badge>
  )
}
