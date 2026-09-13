import { Component, type ErrorInfo, type ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/** Catches render-time errors anywhere in the tree. Never surfaces stack traces to the user - only a generic, safe message and a reload action. */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Logged to the browser console for developers only - never rendered to the user.
    console.error('Unhandled UI error', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-3 bg-background p-6 text-center">
          <div className="flex size-12 items-center justify-center rounded-full bg-destructive-muted">
            <AlertTriangle className="size-6 text-destructive" />
          </div>
          <p className="text-sm font-medium">Something went wrong in the console.</p>
          <p className="max-w-sm text-sm text-muted-foreground">
            Reloading usually resolves this. If it keeps happening, check the browser console for details.
          </p>
          <Button className="mt-2" onClick={() => window.location.reload()}>
            Reload
          </Button>
        </div>
      )
    }
    return this.props.children
  }
}
