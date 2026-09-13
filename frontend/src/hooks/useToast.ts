import { useEffect, useState } from 'react'

export type ToastVariant = 'default' | 'success' | 'destructive'

export interface Toast {
  id: string
  title: string
  description?: string
  variant: ToastVariant
}

type Listener = (toasts: Toast[]) => void

let toasts: Toast[] = []
const listeners = new Set<Listener>()

function emit() {
  for (const listener of listeners) listener(toasts)
}

function dismissToast(id: string) {
  toasts = toasts.filter((t) => t.id !== id)
  emit()
}

/** Framework-agnostic toast store (no external state library) - `useToast` below just subscribes to it. */
export function pushToast(toast: Omit<Toast, 'id'>, durationMs = 5000): string {
  const id = crypto.randomUUID()
  toasts = [...toasts, { ...toast, id }]
  emit()
  setTimeout(() => dismissToast(id), durationMs)
  return id
}

export function toast(title: string, description?: string) {
  return pushToast({ title, description, variant: 'default' })
}

toast.success = (title: string, description?: string) => pushToast({ title, description, variant: 'success' })
toast.error = (title: string, description?: string) => pushToast({ title, description, variant: 'destructive' })

export function useToast() {
  const [state, setState] = useState<Toast[]>(toasts)

  useEffect(() => {
    listeners.add(setState)
    return () => {
      listeners.delete(setState)
    }
  }, [])

  return { toasts: state, dismiss: dismissToast }
}
