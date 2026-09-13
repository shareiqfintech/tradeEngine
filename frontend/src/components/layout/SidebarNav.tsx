import { NavLink } from 'react-router-dom'
import { TerminalSquare } from 'lucide-react'
import { cn } from '@/lib/utils'
import { NAV_ITEMS } from './navItems'

export function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex h-14 items-center gap-2 border-b border-sidebar-border px-4">
        <TerminalSquare className="size-5 text-primary" />
        <span className="text-sm font-semibold tracking-tight text-sidebar-foreground">Trading Engine</span>
      </div>

      <nav className="flex-1 space-y-0.5 overflow-y-auto p-3">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                isActive
                  ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                  : 'text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground',
              )
            }
          >
            <item.icon className="size-4 shrink-0" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-sidebar-border p-3 text-[11px] leading-snug text-sidebar-foreground/50">
        TradingView → Spring Boot → Groww
        <br />
        Automated F&amp;O execution console
      </div>
    </div>
  )
}
