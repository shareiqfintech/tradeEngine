import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { CheckCircle2, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Switch } from '@/components/ui/switch'
import { ErrorState } from '@/components/shared/ErrorState'
import { TableSkeleton } from '@/components/shared/skeletons'
import { NumberStepper } from './NumberStepper'
import { useFnoTradeConfig, useFnoUnderlyings, useUpdateFnoTradeConfig } from '@/hooks/useFnoConfig'
import { useDebouncedValue } from '@/hooks/useDebouncedValue'
import { toast } from '@/hooks/useToast'
import { formatNumber } from '@/lib/format'
import { FNO_EXPIRY_SELECTIONS, FNO_OPTION_TYPE_SELECTIONS, FNO_STRIKE_SELECTIONS } from '@/types'
import type {
  FnoExpirySelection,
  FnoOptionTypeSelection,
  FnoStrikeSelection,
  FnoTradeConfigRequest,
  FnoTradeConfigResponse,
} from '@/types'

interface FormState {
  optionType: FnoOptionTypeSelection
  strikeSelection: FnoStrikeSelection
  strikeOffset: number
  expirySelection: FnoExpirySelection
  lots: number
  /** 'DEFAULT' = no explicit override (backend uses the resolved contract's own current lot size). */
  lotSize: 'DEFAULT' | number
  /** Automatic profit-target size in POINTS. TARGET PRICE = actual Groww fill + targetPoints (backend-calculated). */
  targetPoints: number
  targetEnabled: boolean
}

function deriveForm(config: FnoTradeConfigResponse): FormState {
  return {
    optionType: config.optionType,
    strikeSelection: config.strikeSelection,
    strikeOffset: config.strikeOffset,
    expirySelection: config.expirySelection,
    lots: config.lots,
    lotSize: config.lotSizeSource === 'USER' ? config.lotSize : 'DEFAULT',
    targetPoints: config.targetPoints,
    targetEnabled: config.targetEnabled,
  }
}

/**
 * Outer shell: picks which underlying's configuration is being edited (list
 * sourced live from Groww's instrument master) and loads its current
 * config. The actual editable form is a child keyed by `underlying`, so
 * switching underlyings remounts it with a fresh lazy-initialized state
 * from the newly-loaded config - no effect-based state syncing needed.
 */
export function FnoTradeConfigForm() {
  const underlyingsQuery = useFnoUnderlyings()
  const [underlyingOverride, setUnderlyingOverride] = useState<string | undefined>(undefined)
  const underlying = underlyingOverride ?? underlyingsQuery.data?.[0]
  const configQuery = useFnoTradeConfig(underlying)

  return (
    <Card>
      <CardHeader>
        <CardTitle>F&amp;O Trade Configuration</CardTitle>
        <CardDescription>
          Per-underlying contract-resolution settings. The backend resolves the exact Groww contract and validates
          any lot size you select - nothing here is trusted blindly.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        <Field label="Underlying">
          <Select
            value={underlying}
            onValueChange={setUnderlyingOverride}
            disabled={underlyingsQuery.isLoading || (underlyingsQuery.data?.length ?? 0) === 0}
          >
            <SelectTrigger className="max-w-56">
              <SelectValue placeholder={underlyingsQuery.isLoading ? 'Loading…' : 'Select underlying'} />
            </SelectTrigger>
            <SelectContent>
              {(underlyingsQuery.data ?? []).map((u) => (
                <SelectItem key={u} value={u}>
                  {u}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <Separator />

        {configQuery.isLoading ? (
          <TableSkeleton rows={2} cols={4} />
        ) : configQuery.isError ? (
          <ErrorState error={configQuery.error} onRetry={() => void configQuery.refetch()} />
        ) : configQuery.data && underlying ? (
          <FnoTradeConfigFields key={underlying} underlying={underlying} initialConfig={configQuery.data} />
        ) : null}
      </CardContent>
    </Card>
  )
}

function FnoTradeConfigFields({ underlying, initialConfig }: { underlying: string; initialConfig: FnoTradeConfigResponse }) {
  const [form, setForm] = useState<FormState>(() => deriveForm(initialConfig))
  const [latestConfig, setLatestConfig] = useState<FnoTradeConfigResponse>(initialConfig)
  const updateMutation = useUpdateFnoTradeConfig(underlying)
  const { mutate: applyConfig } = updateMutation

  // BUG FIX: "Selected Contract"/"Lot Size"/"Order Quantity" below are driven
  // by `latestConfig` (the last value returned from the backend), not by the
  // live `form` selections - so changing Expiry/Strike/Option Type/Lots/Lot
  // Size previously had NO visible effect until "Apply Configuration" was
  // clicked (e.g. going from 1 lot to 2 lots kept showing the 1-lot
  // quantity). This re-resolves against the backend (same PUT endpoint, same
  // OptionContractResolver/quantity calc - nothing new) whenever any
  // config-affecting field changes, debounced, so the panel below always
  // reflects the current selection. This is safe to auto-apply because the
  // value being sent is always the user's own current pick, not a value that
  // could unexpectedly overwrite it.
  const configSelectionKey = useMemo(
    () =>
      JSON.stringify({
        optionType: form.optionType,
        strikeSelection: form.strikeSelection,
        strikeOffset: form.strikeOffset,
        expirySelection: form.expirySelection,
        lots: form.lots,
        lotSize: form.lotSize,
        targetPoints: form.targetPoints,
        targetEnabled: form.targetEnabled,
      }),
    [
      form.optionType,
      form.strikeSelection,
      form.strikeOffset,
      form.expirySelection,
      form.lots,
      form.lotSize,
      form.targetPoints,
      form.targetEnabled,
    ],
  )
  const debouncedConfigSelectionKey = useDebouncedValue(configSelectionKey, 400)

  useEffect(() => {
    const next = JSON.parse(debouncedConfigSelectionKey) as FormState
    const alreadyResolved =
      next.optionType === latestConfig.optionType &&
      next.strikeSelection === latestConfig.strikeSelection &&
      next.strikeOffset === latestConfig.strikeOffset &&
      next.expirySelection === latestConfig.expirySelection &&
      next.lots === latestConfig.lots &&
      next.lotSize === (latestConfig.lotSizeSource === 'USER' ? latestConfig.lotSize : 'DEFAULT') &&
      next.targetPoints === latestConfig.targetPoints &&
      next.targetEnabled === latestConfig.targetEnabled
    if (alreadyResolved) return
    if (!(next.targetPoints > 0)) return // never send an invalid Target Points to the backend

    applyConfig(
      {
        optionType: next.optionType,
        strikeSelection: next.strikeSelection,
        strikeOffset: next.strikeOffset,
        expirySelection: next.expirySelection,
        lots: next.lots,
        lotSize: next.lotSize === 'DEFAULT' ? null : next.lotSize,
        targetPoints: next.targetPoints,
        targetEnabled: next.targetEnabled,
      },
      { onSuccess: (response) => setLatestConfig(response) },
    )
  }, [debouncedConfigSelectionKey, latestConfig, applyConfig])

  const isDirty =
    form.optionType !== latestConfig.optionType ||
    form.strikeSelection !== latestConfig.strikeSelection ||
    form.strikeOffset !== latestConfig.strikeOffset ||
    form.expirySelection !== latestConfig.expirySelection ||
    form.lots !== latestConfig.lots ||
    form.lotSize !== (latestConfig.lotSizeSource === 'USER' ? latestConfig.lotSize : 'DEFAULT') ||
    form.targetPoints !== latestConfig.targetPoints ||
    form.targetEnabled !== latestConfig.targetEnabled

  function handleApply() {
    const request: FnoTradeConfigRequest = {
      optionType: form.optionType,
      strikeSelection: form.strikeSelection,
      strikeOffset: form.strikeOffset,
      expirySelection: form.expirySelection,
      lots: form.lots,
      lotSize: form.lotSize === 'DEFAULT' ? null : form.lotSize,
      targetPoints: form.targetPoints,
      targetEnabled: form.targetEnabled,
    }
    updateMutation.mutate(request, {
      onSuccess: (response) => {
        setLatestConfig(response)
        toast.success('F&O configuration applied', `${underlying} contract resolved and saved.`)
      },
    })
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Expiry">
          <Select value={form.expirySelection} onValueChange={(v) => setForm((f) => ({ ...f, expirySelection: v as FnoExpirySelection }))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FNO_EXPIRY_SELECTIONS.map((v) => (
                <SelectItem key={v} value={v}>
                  {v === 'NEAREST' ? 'Nearest' : 'Next'}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <Field label="Strike">
          <Select value={form.strikeSelection} onValueChange={(v) => setForm((f) => ({ ...f, strikeSelection: v as FnoStrikeSelection }))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FNO_STRIKE_SELECTIONS.map((v) => (
                <SelectItem key={v} value={v}>
                  {v}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <Field label="Option Type">
          <Select value={form.optionType} onValueChange={(v) => setForm((f) => ({ ...f, optionType: v as FnoOptionTypeSelection }))}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FNO_OPTION_TYPE_SELECTIONS.map((v) => (
                <SelectItem key={v} value={v}>
                  {v}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        {form.strikeSelection !== 'ATM' && (
          <Field label="Strike Offset (steps from ATM)">
            <NumberStepper value={form.strikeOffset} min={0} onChange={(v) => setForm((f) => ({ ...f, strikeOffset: v }))} />
          </Field>
        )}
      </div>

      <Separator />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Selected Contract">
          <p className="font-mono text-sm font-semibold">
            {latestConfig.resolvedContract.underlying} {latestConfig.resolvedContract.expiry}{' '}
            {formatNumber(latestConfig.resolvedContract.strike)} {latestConfig.resolvedContract.optionType}
          </p>
          <p className="mt-0.5 text-xs text-muted-foreground">{latestConfig.resolvedContract.tradingSymbol}</p>
        </Field>

        <Field label="Lot Size">
          <Select
            value={String(form.lotSize)}
            onValueChange={(v) => setForm((f) => ({ ...f, lotSize: v === 'DEFAULT' ? 'DEFAULT' : Number(v) }))}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="DEFAULT">Contract default ({latestConfig.resolvedContract.lotSize})</SelectItem>
              {latestConfig.validLotSizes.map((size) => (
                <SelectItem key={size} value={String(size)}>
                  {size}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Badge variant={latestConfig.lotSizeSource === 'USER' ? 'info' : 'muted'} className="mt-1">
            {latestConfig.lotSizeSource === 'USER' ? 'User-selected' : 'Contract default'}
          </Badge>
        </Field>

        <Field label="Lots">
          <NumberStepper value={form.lots} min={1} onChange={(v) => setForm((f) => ({ ...f, lots: v }))} />
        </Field>

        <Field label="Order Quantity">
          <p className="font-mono text-2xl font-semibold tabular-nums">{formatNumber(latestConfig.quantity)}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {latestConfig.lots} lot{latestConfig.lots === 1 ? '' : 's'} × {latestConfig.lotSize} lot size
          </p>
        </Field>
      </div>

      <Separator />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Field label="Target Points">
          <NumberStepper
            value={form.targetPoints}
            min={latestConfig.targetPointsMin}
            max={latestConfig.targetPointsMax}
            step={1}
            disabled={!form.targetEnabled}
            onChange={(v) => setForm((f) => ({ ...f, targetPoints: v }))}
          />
          <p className="mt-1 text-xs text-muted-foreground">
            User configuration. Range {latestConfig.targetPointsMin}–{latestConfig.targetPointsMax}.
          </p>
        </Field>

        <Field label="Target Price">
          <p className="font-mono text-sm font-semibold text-muted-foreground">Calculated after entry</p>
          <p className="mt-1 text-xs text-muted-foreground">
            = actual Groww fill + Target Points (backend-calculated, never from the signal price).
          </p>
        </Field>

        <Field label="Auto-Exit at Target">
          <div className="flex items-center gap-2">
            <Switch
              checked={form.targetEnabled}
              onCheckedChange={(checked) => setForm((f) => ({ ...f, targetEnabled: checked }))}
            />
            <span className="text-sm">{form.targetEnabled ? 'Enabled' : 'Disabled'}</span>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">Applies to NEW positions on this underlying.</p>
        </Field>
      </div>

      <div className="flex items-center gap-3">
        {/* BUG FIX: the debounced auto-resolve effect above now applies every
            field (including lots/lotSize), so `form` catches up to
            `latestConfig` moments after any change - `isDirty` collapses to
            false almost immediately, which previously kept this button
            disabled nearly all the time via `!isDirty`. Manual Apply must
            still work regardless of that transient state, so only the
            in-flight request gates it now; clicking while already synced is
            a harmless no-op re-apply of the current selection. */}
        <Button onClick={handleApply} disabled={updateMutation.isPending}>
          {updateMutation.isPending ? (
            <>
              <RefreshCw className="size-4 animate-spin" />
              Resolving…
            </>
          ) : (
            <>
              <CheckCircle2 className="size-4" />
              Apply Configuration
            </>
          )}
        </Button>
        {isDirty && !updateMutation.isPending && <span className="text-xs text-muted-foreground">Unsaved changes</span>}
      </div>
    </div>
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
