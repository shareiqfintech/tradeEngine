import { LogOut, User } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Separator } from '@/components/ui/separator'
import { GrowwStatusBadge } from '@/components/status/GrowwStatusBadge'
import { MarketStatusBadge } from '@/components/status/MarketStatusBadge'
import { TradingModeBadge } from '@/components/status/TradingModeBadge'
import { ComponentHealthDot } from '@/components/status/ComponentHealthDot'
import { useAuth } from '@/app/AuthProvider'
import { useActuatorHealth } from '@/hooks/useHealth'
import { useIndiaClock } from '@/hooks/useIndiaClock'
import { useTradingStatus } from '@/hooks/useTradingStatus'
import type { ComponentHealth } from '@/types'
import { MobileNav } from './MobileNav'

function useOverallHealth(): ComponentHealth {
  const { data, isError, isLoading } = useActuatorHealth()
  if (isLoading) return 'UNKNOWN'
  if (isError) return 'DOWN'
  if (data?.status === 'UP') return 'HEALTHY'
  if (data?.status === 'DOWN' || data?.status === 'OUT_OF_SERVICE') return 'DOWN'
  return 'UNKNOWN'
}

export function TopBar() {
  const { data: status, isError: statusError } = useTradingStatus()
  const health = useOverallHealth()
  const { time, date } = useIndiaClock()
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/signin', { replace: true })
  }

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-border bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/80">
      <MobileNav />

      <span className="hidden text-sm font-semibold tracking-tight lg:hidden xl:inline">Trading Engine Console</span>

      <div className="flex flex-1 items-center justify-end gap-2 overflow-x-auto">
        <TradingModeBadge mode={status?.mode ?? 'PAPER'} />
        <MarketStatusBadge />
        <GrowwStatusBadge authenticated={statusError ? undefined : status?.growwAuthenticated} />
        <Separator orientation="vertical" className="h-5" />
        <ComponentHealthDot status={health} className="hidden sm:flex" />
        <Separator orientation="vertical" className="hidden h-5 sm:block" />

        <div className="hidden flex-col items-end leading-tight md:flex">
          <span className="font-mono text-sm font-medium tabular-nums">{time} IST</span>
          <span className="text-[11px] text-muted-foreground">{date}</span>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger className="ml-1 rounded-full outline-none focus-visible:ring-2 focus-visible:ring-ring">
            <Avatar>
              <AvatarFallback>
                <User className="size-4" />
              </AvatarFallback>
            </Avatar>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuLabel>{user?.name ?? 'Account'}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleLogout}>
              <LogOut className="size-4" />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
