import { useState } from 'react'
import { AlertOctagon, Pause, Play, ShieldOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { ConfirmationDialog } from '@/components/shared/ConfirmationDialog'
import {
  useDisableKillSwitch,
  useEnableKillSwitch,
  usePauseTrading,
  useResumeTrading,
  useTradingStatus,
} from '@/hooks/useTradingStatus'
import { deriveTradingStatusLabel } from './tradingStatusLabel'
import { cn } from '@/lib/utils'

const STATUS_BADGE: Record<ReturnType<typeof deriveTradingStatusLabel>, { label: string; className: string }> = {
  ENABLED: { label: 'TRADING ENABLED', className: 'bg-success-muted text-success' },
  PAUSED: { label: 'TRADING PAUSED', className: 'bg-warning-muted text-warning' },
  KILL_SWITCH: { label: 'KILL SWITCH ACTIVE', className: 'bg-destructive-muted text-destructive' },
  AUTH_REQUIRED: { label: 'GROWW AUTH REQUIRED', className: 'bg-warning-muted text-warning' },
  SESSION_CLOSING: { label: 'SAFETY EXIT (CLOSING)', className: 'bg-warning-muted text-warning' },
  MARKET_CLOSED: { label: 'MARKET CLOSED', className: 'bg-muted text-muted-foreground' },
}

/**
 * The dedicated Trading Controls card. Every button here calls a REAL
 * backend endpoint (POST /api/trading/{pause,resume,kill-switch/enable,kill-switch/disable})
 * - there is no client-side trading logic. The backend remains the sole
 * authority; this component only sends the operator's intent and reflects
 * whatever the backend reports back.
 */
export function TradingControls() {
  const { data: status, isLoading } = useTradingStatus()
  const pauseMutation = usePauseTrading()
  const resumeMutation = useResumeTrading()
  const enableKillSwitchMutation = useEnableKillSwitch()
  const disableKillSwitchMutation = useDisableKillSwitch()

  const [confirmKillSwitch, setConfirmKillSwitch] = useState(false)

  const label = status ? deriveTradingStatusLabel(status) : null
  const badge = label ? STATUS_BADGE[label] : null

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-3">
          <div>
            <CardTitle>Trading Controls</CardTitle>
            <CardDescription>Sends authorized control commands to the backend. The backend decides everything else.</CardDescription>
          </div>
          {badge && (
            <Badge className={cn('px-2.5 py-1 text-xs font-semibold', badge.className)} variant="outline">
              {badge.label}
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent className="flex flex-wrap gap-2">
        {!status?.paused ? (
          <Button
            variant="outline"
            disabled={isLoading || pauseMutation.isPending}
            onClick={() => pauseMutation.mutate()}
          >
            <Pause className="size-4" />
            Pause Trading
          </Button>
        ) : (
          <Button
            variant="outline"
            disabled={isLoading || resumeMutation.isPending}
            onClick={() => resumeMutation.mutate()}
          >
            <Play className="size-4" />
            Resume Trading
          </Button>
        )}

        {!status?.killSwitch ? (
          <Button
            variant="destructive"
            disabled={isLoading}
            onClick={() => setConfirmKillSwitch(true)}
          >
            <AlertOctagon className="size-4" />
            Enable Kill Switch
          </Button>
        ) : (
          <Button
            variant="outline"
            disabled={isLoading || disableKillSwitchMutation.isPending}
            onClick={() => disableKillSwitchMutation.mutate()}
          >
            <ShieldOff className="size-4" />
            Disable Kill Switch
          </Button>
        )}
      </CardContent>

      <ConfirmationDialog
        open={confirmKillSwitch}
        onOpenChange={setConfirmKillSwitch}
        title="Enable Kill Switch?"
        description="New orders will be blocked immediately. This does NOT close any existing positions - only the backend's TradingEngineService can decide that, and it will not do so automatically."
        confirmLabel="Enable Kill Switch"
        destructive
        loading={enableKillSwitchMutation.isPending}
        onConfirm={() =>
          enableKillSwitchMutation.mutate(undefined, {
            onSuccess: () => setConfirmKillSwitch(false),
          })
        }
      />
    </Card>
  )
}
