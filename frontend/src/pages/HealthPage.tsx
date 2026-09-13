import { Boxes, Cable, Database, Gauge, Server, Webhook } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { HealthCard } from '@/components/health/HealthCard'
import { StatCardSkeleton } from '@/components/shared/skeletons'
import { useActuatorHealth, useHealthSummary } from '@/hooks/useHealth'
import { useTradingStatus } from '@/hooks/useTradingStatus'
import { formatDateTime } from '@/lib/format'
import type { ActuatorHealthComponent, ComponentHealth } from '@/types'

function fromComponent(component: ActuatorHealthComponent | undefined): ComponentHealth {
  if (!component) return 'UNKNOWN'
  if (component.status === 'UP') return 'HEALTHY'
  if (component.status === 'DOWN' || component.status === 'OUT_OF_SERVICE') return 'DOWN'
  return 'DEGRADED'
}

function formatUptime(seconds: number | null | undefined): string {
  if (seconds == null) return '—'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  return `${hours}h ${minutes}m`
}

export function HealthPage() {
  const actuator = useActuatorHealth()
  const summary = useHealthSummary()
  const status = useTradingStatus()

  const springBootHealth: ComponentHealth = actuator.isLoading ? 'UNKNOWN' : actuator.isError ? 'DOWN' : 'HEALTHY'
  const dbHealth = fromComponent(actuator.data?.components?.db)
  const redisHealth = fromComponent(actuator.data?.components?.redis)
  const growwHealth: ComponentHealth = status.isError
    ? 'UNKNOWN'
    : status.data?.growwAuthenticated
      ? 'HEALTHY'
      : 'DEGRADED'
  const engineHealth: ComponentHealth = status.isError ? 'UNKNOWN' : status.data?.tradingEnabled ? 'HEALTHY' : 'DEGRADED'

  const componentsExposed = actuator.data?.components !== undefined

  return (
    <div>
      <PageHeader title="System Health" description="Backend, database, cache, and broker connectivity - all sourced from the real backend." />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {actuator.isLoading ? (
          <>
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </>
        ) : (
          <>
            <HealthCard name="Spring Boot" status={springBootHealth} icon={Server} detail="GET /actuator/health" />
            <HealthCard
              name="MySQL"
              status={dbHealth}
              icon={Database}
              detail={componentsExposed ? 'via Actuator health components' : 'Backend needs management.endpoint.health.show-details'}
              notExposed={!componentsExposed}
            />
            <HealthCard
              name="Redis"
              status={redisHealth}
              icon={Boxes}
              detail={componentsExposed ? 'via Actuator health components' : 'Backend needs management.endpoint.health.show-details'}
              notExposed={!componentsExposed}
            />
            <HealthCard name="Groww API" status={growwHealth} icon={Cable} detail="Derived from growwAuthenticated" />
            <HealthCard name="Trading Engine" status={engineHealth} icon={Gauge} detail="Derived from tradingEnabled" />
            <HealthCard name="TradingView Webhook" status="UNKNOWN" icon={Webhook} detail="No last-received timestamp endpoint yet" notExposed />
          </>
        )}
      </div>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Operational Details</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Detail label="Last Groww Authentication" value={summary.data ? formatDateTime(summary.data.lastGrowwAuthAt) : null} />
          <Detail label="Last Webhook Received" value={summary.data ? formatDateTime(summary.data.lastWebhookReceivedAt) : null} />
          <Detail label="Last Order" value={summary.data ? formatDateTime(summary.data.lastOrderAt) : null} />
          <Detail label="Application Uptime" value={summary.data ? formatUptime(summary.data.applicationUptimeSeconds) : null} />
        </CardContent>
        {summary.isError && (
          <div className="border-t border-border px-4 py-3 text-xs text-muted-foreground">
            Not available yet - requires a <code className="rounded bg-muted px-1 py-0.5">GET /api/health/summary</code> backend endpoint. See frontend/README.md.
          </div>
        )}
      </Card>
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string | null }) {
  return (
    <div>
      <div className="text-xs text-muted-foreground">{label}</div>
      <div className="font-mono text-sm font-medium">{value ?? '—'}</div>
    </div>
  )
}
