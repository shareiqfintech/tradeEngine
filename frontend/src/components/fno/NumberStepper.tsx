import { useState } from 'react'
import { Minus, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * `[-] value [+]` with an editable middle. `step` drives the +/- buttons
 * (default 1); free-typed values are parsed and clamped to `[min, max]` on
 * change (when valid) and again on blur. Backward compatible with the
 * previous integer-only usages (which pass only `value` / `min` / `onChange`).
 */
export function NumberStepper({
  value,
  min = 0,
  max,
  step = 1,
  onChange,
  disabled,
}: {
  value: number
  min?: number
  max?: number
  step?: number
  onChange: (next: number) => void
  disabled?: boolean
}) {
  const [draft, setDraft] = useState(String(value))
  // Sync the editable draft when the prop changes (render-phase, not an effect).
  const [syncedValue, setSyncedValue] = useState(value)
  if (value !== syncedValue) {
    setSyncedValue(value)
    setDraft(String(value))
  }

  const clamp = (n: number) => {
    let v = n
    if (v < min) v = min
    if (max != null && v > max) v = max
    // avoid 0.1 + 0.2 style drift for decimal steps
    return Number(v.toFixed(6))
  }

  const commit = (raw: string) => {
    const parsed = Number(raw)
    if (raw.trim() === '' || Number.isNaN(parsed)) {
      setDraft(String(value))
      return
    }
    const next = clamp(parsed)
    setDraft(String(next))
    if (next !== value) onChange(next)
  }

  return (
    <div className="inline-flex items-center gap-2">
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="size-8"
        disabled={disabled || value <= min}
        onClick={() => onChange(clamp(value - step))}
      >
        <Minus className="size-3.5" />
      </Button>
      <input
        type="number"
        inputMode="decimal"
        step={step}
        min={min}
        max={max}
        value={draft}
        disabled={disabled}
        onChange={(e) => {
          setDraft(e.target.value)
          const parsed = Number(e.target.value)
          if (e.target.value.trim() !== '' && !Number.isNaN(parsed)) {
            const next = clamp(parsed)
            if (next !== value) onChange(next)
          }
        }}
        onBlur={(e) => commit(e.target.value)}
        className={cn(
          'w-16 rounded-md border border-input bg-transparent px-2 py-1 text-center font-mono text-sm font-semibold tabular-nums',
          'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
          '[appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none',
        )}
      />
      <Button
        type="button"
        variant="outline"
        size="icon"
        className="size-8"
        disabled={disabled || (max != null && value >= max)}
        onClick={() => onChange(clamp(value + step))}
      >
        <Plus className="size-3.5" />
      </Button>
    </div>
  )
}
