import { useState } from 'react'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ConfirmationDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: string
  confirmLabel: string
  cancelLabel?: string
  destructive?: boolean
  loading?: boolean
  /** If set, the confirm button stays disabled until the user types this exact string - used for the LIVE-mode switch. */
  requireTypedConfirmation?: string
  onConfirm: () => void
}

export function ConfirmationDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  cancelLabel = 'Cancel',
  destructive = false,
  loading = false,
  requireTypedConfirmation,
  onConfirm,
}: ConfirmationDialogProps) {
  const [typedValue, setTypedValue] = useState('')

  const isTypingGateSatisfied = !requireTypedConfirmation || typedValue === requireTypedConfirmation

  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setTypedValue('')
        onOpenChange(next)
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>

        {requireTypedConfirmation && (
          <div className="space-y-2">
            <Label htmlFor="confirm-input">
              Type <span className="font-mono font-semibold text-foreground">{requireTypedConfirmation}</span> to confirm
            </Label>
            <Input
              id="confirm-input"
              autoComplete="off"
              value={typedValue}
              onChange={(e) => setTypedValue(e.target.value)}
              placeholder={requireTypedConfirmation}
            />
          </div>
        )}

        <AlertDialogFooter>
          <AlertDialogCancel disabled={loading}>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            disabled={loading || !isTypingGateSatisfied}
            onClick={(e) => {
              e.preventDefault()
              onConfirm()
            }}
            className={cn(destructive && buttonVariants({ variant: 'destructive' }))}
          >
            {loading ? 'Working…' : confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
