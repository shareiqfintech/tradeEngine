import { Link } from 'react-router-dom'
import { CompassIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/shared/EmptyState'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <EmptyState
        icon={CompassIcon}
        title="Page not found"
        description="The page you're looking for doesn't exist in the trading engine console."
        action={
          <Button asChild>
            <Link to="/dashboard">Back to Dashboard</Link>
          </Button>
        }
      />
    </div>
  )
}
