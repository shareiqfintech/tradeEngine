import type { ReactNode } from 'react'
import { KeyRound } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { TableSkeleton } from '@/components/shared/skeletons'
import { ErrorState } from '@/components/shared/ErrorState'
import { useTradingConfig } from '@/hooks/useSettings'
import { TradingModeBadge } from '@/components/status/TradingModeBadge'
import { FnoTradeConfigForm } from '@/components/fno/FnoTradeConfigForm'
import { GrowwSettingsForm } from '@/components/settings/GrowwSettingsForm'

export function SettingsPage() {
  const { data: config, isLoading, isError, error, refetch } = useTradingConfig()

  return (
    <div>
      <PageHeader
        title="Settings"
        description="Read-only view of the trading engine's server-side configuration."
      />

      <Card className="mb-6 border-info/30 bg-info-muted/40">
        <CardContent className="flex items-center gap-3 p-4 text-sm">
          <KeyRound className="size-4 shrink-0 text-info" />
          <p>
            Credentials are securely managed by the server. The Groww API key, TOTP secret, access token, database
            password, Redis password, and TradingView webhook secret are never sent to this frontend.
          </p>
        </CardContent>
      </Card>

      <GrowwSettingsForm />

      <div className="mb-6">
        <FnoTradeConfigForm />
      </div>

      {isLoading ? (
        <TableSkeleton rows={6} cols={2} />
      ) : isError || !config ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <SettingsSection title="Trading" description="Core trading mode and market hours">
            <Row label="Mode"><TradingModeBadge mode={config.mode} /></Row>
            <Row label="Session Start">{config.market.start}</Row>
            <Row label="Trading Cutoff (safety exit)">{config.market.tradingCutoff}</Row>
            <Row label="Official Market Close">{config.market.close}</Row>
            <Row label="Timezone">{config.timezone}</Row>
          </SettingsSection>

          <SettingsSection title="Risk" description="Limits enforced by RiskManagementService">
            <Row label="Max Orders / Day">{config.risk.maxOrdersPerDay}</Row>
            <Row label="Max Open Positions">{config.risk.maxOpenPositions}</Row>
            <Row label="Max Quantity">{config.risk.maxQuantity}</Row>
            <Row label="Max Daily Loss">₹{config.risk.maxDailyLoss.toLocaleString('en-IN')}</Row>
            <Row label="Max Orders / Signal">{config.risk.maxOrdersPerSignal}</Row>
            <Row label="Short Selling">
              <Badge variant={config.allowShortSelling ? 'warning' : 'muted'}>{config.allowShortSelling ? 'ON' : 'OFF'}</Badge>
            </Row>
          </SettingsSection>

          <SettingsSection title="Option" description="Server-config default, used for any underlying without an explicit override above">
            <Row label="Expiry Selection">{config.option.expirySelection}</Row>
            <Row label="Strike Selection">{config.option.strikeSelection}</Row>
            <Row label="Option Type">{config.option.optionType}</Row>
            <Row label="Strike Offset">{config.option.strikeOffset}</Row>
            <Row label="Lots">{config.quantity.lots}</Row>
          </SettingsSection>

          <SettingsSection title="Order" description="Defaults applied to every order request">
            <Row label="Product">{config.order.product}</Row>
            <Row label="Order Type">{config.order.orderType}</Row>
            <Row label="Validity">{config.order.validity}</Row>
          </SettingsSection>
        </div>
      )}
    </div>
  )
}

function SettingsSection({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3 text-sm">{children}</CardContent>
    </Card>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{children}</span>
    </div>
  )
}
