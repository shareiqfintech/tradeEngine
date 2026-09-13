import { AlertTriangle, PlugZap, RefreshCw, ServerCrash } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/api/errors'

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const apiError = error instanceof ApiError ? error : null

  const isBackendUnreachable = apiError?.kind === 'network'
  const isNotImplemented = apiError?.kind === 'not_found' || apiError?.kind === 'not_implemented'

  const Icon = isBackendUnreachable ? PlugZap : isNotImplemented ? ServerCrash : AlertTriangle

  const title = isBackendUnreachable
    ? 'Trading engine is unavailable'
    : isNotImplemented
      ? 'This data is not available yet'
      : 'Something went wrong'

  const description = isBackendUnreachable
    ? 'Could not reach the Spring Boot backend. Check that it is running and that VITE_API_BASE_URL / the dev proxy is configured correctly.'
    : isNotImplemented
      ? 'The backend endpoint this page depends on has not been implemented yet. See frontend/README.md "API contract assumptions" for the documented expected contract.'
      : apiError?.message ?? 'An unexpected error occurred while talking to the trading engine.'

  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-destructive/40 bg-destructive-muted/40 px-6 py-16 text-center">
      <div className="mb-2 flex size-12 items-center justify-center rounded-full bg-destructive-muted">
        <Icon className="size-6 text-destructive" />
      </div>
      <p className="text-sm font-medium">{title}</p>
      <p className="max-w-md text-sm text-muted-foreground">{description}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-3" onClick={onRetry}>
          <RefreshCw className="size-3.5" />
          Retry
        </Button>
      )}
    </div>
  )
}
