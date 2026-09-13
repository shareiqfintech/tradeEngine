import {
  Activity,
  Gauge,
  HeartPulse,
  Layers,
  ReceiptText,
  Radio,
  Settings,
  ShieldAlert,
} from 'lucide-react'

export interface NavItem {
  label: string
  to: string
  icon: typeof Gauge
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', to: '/dashboard', icon: Gauge },
  { label: 'Signals', to: '/signals', icon: Radio },
  { label: 'Orders', to: '/orders', icon: ReceiptText },
  { label: 'Positions', to: '/positions', icon: Layers },
  { label: 'Risk', to: '/risk', icon: ShieldAlert },
  { label: 'Activity', to: '/activity', icon: Activity },
  { label: 'System Health', to: '/health', icon: HeartPulse },
  { label: 'Settings', to: '/settings', icon: Settings },
]
