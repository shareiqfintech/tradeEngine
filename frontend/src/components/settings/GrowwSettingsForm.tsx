import { useState, type ReactNode } from 'react'
import { CheckCircle2, RefreshCw, XCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { ErrorState } from '@/components/shared/ErrorState'
import { TableSkeleton } from '@/components/shared/skeletons'
import { useGrowwSettings, useSaveGrowwSettings, useTestGrowwConnection } from '@/hooks/useGrowwSettings'
import type { GrowwConnectionTestResponse } from '@/api/growwSettingsApi'

/**
 * Collects the user's Groww API key + TOTP configuration and stores them
 * server-side (encrypted, per-user) - it does NOT implement TOTP
 * generation or Groww authentication itself; "Test Connection" calls the
 * backend, which reuses the existing TotpGenerator/GrowwApiClient against
 * the real Groww API.
 */
export function GrowwSettingsForm() {
  const settingsQuery = useGrowwSettings()
  const saveMutation = useSaveGrowwSettings()
  const testMutation = useTestGrowwConnection()

  const [apiKeyInput, setApiKeyInput] = useState('')
  const [totpInput, setTotpInput] = useState('')

  if (settingsQuery.isLoading) {
    return (
      <Card className="mb-6">
        <CardContent className="pt-6">
          <TableSkeleton rows={2} cols={2} />
        </CardContent>
      </Card>
    )
  }

  if (settingsQuery.isError || !settingsQuery.data) {
    return (
      <Card className="mb-6">
        <CardContent className="pt-6">
          <ErrorState error={settingsQuery.error} onRetry={() => void settingsQuery.refetch()} />
        </CardContent>
      </Card>
    )
  }

  const settings = settingsQuery.data

  function handleSave() {
    // Only include a field if the user actually typed something new - an
    // untouched field is omitted entirely so the backend leaves the
    // currently-saved value unchanged (see GrowwSettingsRequest contract).
    saveMutation.mutate(
      {
        ...(apiKeyInput ? { apiKey: apiKeyInput } : {}),
        ...(totpInput ? { totpSecret: totpInput } : {}),
      },
      {
        onSuccess: () => {
          setApiKeyInput('')
          setTotpInput('')
        },
      },
    )
  }

  return (
    <Card className="mb-6">
      <CardHeader>
        <div className="flex items-center justify-between gap-3">
          <CardTitle>Groww Configuration</CardTitle>
          <Badge variant={settings.connected ? 'success' : 'muted'}>
            {settings.connected ? 'Connected' : 'Not connected'}
          </Badge>
        </div>
        <CardDescription>
          Your Groww API key and TOTP configuration, used only to authenticate your own account - never shared with
          other users, and never sent back to this browser in full.
          {settings.configured && !settings.connected && ' Your credentials changed since the last successful connection - run Test Connection again.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Groww API Key">
            <Input
              type="password"
              placeholder={settings.apiKeyConfigured ? settings.maskedApiKey ?? '••••••••••••' : 'Enter your Groww API key'}
              value={apiKeyInput}
              onChange={(e) => setApiKeyInput(e.target.value)}
              autoComplete="off"
            />
            <Badge variant={settings.apiKeyConfigured ? 'info' : 'muted'} className="mt-1">
              {settings.apiKeyConfigured ? 'Saved' : 'Not configured'}
            </Badge>
          </Field>

          <Field label="Groww TOTP Token / Configuration">
            <Input
              type="password"
              placeholder={settings.totpConfigured ? '••••••••••••' : 'Enter your Groww TOTP secret'}
              value={totpInput}
              onChange={(e) => setTotpInput(e.target.value)}
              autoComplete="off"
            />
            <Badge variant={settings.totpConfigured ? 'info' : 'muted'} className="mt-1">
              {settings.totpConfigured ? 'Saved' : 'Not configured'}
            </Badge>
          </Field>
        </div>

        <Separator />

        <div className="flex flex-wrap items-center gap-3">
          <Button
            onClick={handleSave}
            disabled={saveMutation.isPending || (!apiKeyInput && !totpInput)}
          >
            {saveMutation.isPending ? 'Saving…' : 'Save'}
          </Button>

          <Button
            variant="outline"
            onClick={() => testMutation.mutate()}
            disabled={testMutation.isPending || !settings.configured}
          >
            {testMutation.isPending ? (
              <>
                <RefreshCw className="size-4 animate-spin" />
                Testing…
              </>
            ) : (
              'Test Groww Connection'
            )}
          </Button>

          {!settings.configured && (
            <span className="text-xs text-muted-foreground">Save both fields before testing the connection.</span>
          )}

          <ConnectionResult result={testMutation.data} />
        </div>
      </CardContent>
    </Card>
  )
}

function ConnectionResult({ result }: { result: GrowwConnectionTestResponse | undefined }) {
  if (!result) return null
  return (
    <span className={`flex items-center gap-1.5 text-sm ${result.connected ? 'text-success' : 'text-destructive'}`}>
      {result.connected ? <CheckCircle2 className="size-4" /> : <XCircle className="size-4" />}
      {result.message}
    </span>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <Label className="text-xs text-muted-foreground">{label}</Label>
      {children}
    </div>
  )
}
