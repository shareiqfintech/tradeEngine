import { CheckCircle2, HelpCircle, XCircle } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

interface GrowwStatusBadgeProps {
  /** undefined = status could not be determined (e.g. the status call itself failed). */
  authenticated: boolean | undefined
  className?: string
}

/**
 * The backend only exposes a boolean (`growwAuthenticated` on
 * GET /api/trading/status) - not the full GrowwAuthState state machine
 * (AUTHENTICATED/AUTH_REQUIRED/AUTH_FAILED/TOKEN_EXPIRED) that exists
 * server-side. This badge honestly reflects that: AUTHENTICATED,
 * AUTH_REQUIRED, or UNKNOWN (when we can't even reach the status endpoint).
 */
export function GrowwStatusBadge({ authenticated, className }: GrowwStatusBadgeProps) {
  if (authenticated === undefined) {
    return (
      <Badge variant="muted" className={cn('font-medium', className)}>
        <HelpCircle className="size-3" />
        UNKNOWN
      </Badge>
    )
  }

  if (authenticated) {
    return (
      <Badge variant="success" className={cn('font-medium', className)}>
        <CheckCircle2 className="size-3" />
        AUTHENTICATED
      </Badge>
    )
  }

  return (
    <Badge variant="warning" className={cn('font-medium', className)}>
      <XCircle className="size-3" />
      AUTH_REQUIRED
    </Badge>
  )
}
