import {
  AlertTriangle,
  Ban,
  CalendarClock,
  CheckCircle2,
  Copy,
  DoorClosed,
  FileSearch,
  Key,
  KeyRound,
  LogOut,
  type LucideIcon,
  Pause,
  Play,
  Radio,
  RefreshCcw,
  Save,
  ShieldAlert,
  ShieldCheck,
  ShieldOff,
  ShoppingCart,
  Sunrise,
  Target,
  Timer,
  TrendingUp,
  WifiOff,
  XCircle,
} from 'lucide-react'
import type { AuditEventType } from '@/types'

export type AuditTone = 'success' | 'destructive' | 'warning' | 'info' | 'muted'

export interface AuditEventMeta {
  label: string
  icon: LucideIcon
  tone: AuditTone
}

export const AUDIT_EVENT_META: Record<AuditEventType, AuditEventMeta> = {
  SIGNAL_RECEIVED: { label: 'Signal received', icon: Radio, tone: 'info' },
  SIGNAL_SAVED: { label: 'Signal saved', icon: Save, tone: 'info' },
  SIGNAL_VALIDATED: { label: 'Signal validated', icon: CheckCircle2, tone: 'info' },
  DUPLICATE_SIGNAL: { label: 'Duplicate signal ignored', icon: Copy, tone: 'muted' },
  SIGNAL_REJECTED: { label: 'Signal rejected', icon: XCircle, tone: 'destructive' },
  MARKET_CLOSED: { label: 'Market closed', icon: Timer, tone: 'warning' },
  TRADING_DISABLED: { label: 'Trading disabled', icon: ShieldOff, tone: 'warning' },
  AUTH_SUCCESS: { label: 'Groww authentication succeeded', icon: KeyRound, tone: 'success' },
  AUTH_FAILED: { label: 'Groww authentication failed', icon: Key, tone: 'destructive' },
  TOKEN_EXPIRED: { label: 'Groww token expired', icon: AlertTriangle, tone: 'warning' },
  POSITION_CHECK: { label: 'Position check', icon: FileSearch, tone: 'info' },
  NO_LONG_POSITION: { label: 'No long position to sell', icon: Ban, tone: 'destructive' },
  POSITION_CLOSE_VERIFIED: { label: 'Position close verified', icon: ShieldCheck, tone: 'success' },
  POSITION_CLOSE_INCOMPLETE: { label: 'Position close incomplete', icon: AlertTriangle, tone: 'destructive' },
  CONTRACT_RESOLVED: { label: 'Option contract resolved', icon: CheckCircle2, tone: 'success' },
  CONTRACT_NOT_FOUND: { label: 'Option contract not found', icon: XCircle, tone: 'destructive' },
  RISK_APPROVED: { label: 'Risk check approved', icon: ShieldCheck, tone: 'success' },
  RISK_REJECTED: { label: 'Risk check rejected', icon: ShieldAlert, tone: 'destructive' },
  PAPER_ORDER: { label: 'Paper order created', icon: ShoppingCart, tone: 'info' },
  LIVE_ORDER_SUBMITTED: { label: 'Live order submitted to Groww', icon: ShoppingCart, tone: 'warning' },
  ORDER_REJECTED: { label: 'Order rejected', icon: XCircle, tone: 'destructive' },
  ORDER_STATUS_UPDATED: { label: 'Order status updated', icon: RefreshCcw, tone: 'info' },
  ORDER_COMPLETED: { label: 'Order completed', icon: CheckCircle2, tone: 'success' },
  RECONCILIATION_MISMATCH: { label: 'Reconciliation mismatch', icon: AlertTriangle, tone: 'destructive' },
  KILL_SWITCH_ENABLED: { label: 'Kill switch enabled', icon: ShieldOff, tone: 'destructive' },
  KILL_SWITCH_DISABLED: { label: 'Kill switch disabled', icon: ShieldCheck, tone: 'success' },
  TRADING_PAUSED: { label: 'Trading paused', icon: Pause, tone: 'warning' },
  TRADING_RESUMED: { label: 'Trading resumed', icon: Play, tone: 'success' },

  // Trading-session lifecycle (09:25 start / 15:10 safety exit / 15:30 official close)
  SESSION_STARTED: { label: 'Trading session started (09:25 IST)', icon: Sunrise, tone: 'success' },
  NEW_ENTRY_REJECTED_SESSION_NOT_STARTED: { label: 'New entry rejected - session not started', icon: CalendarClock, tone: 'muted' },
  NEW_ENTRY_REJECTED_TRADING_CUTOFF: { label: 'New entry rejected - 15:10 safety cutoff', icon: Timer, tone: 'warning' },
  SESSION_CLOSING_STARTED: { label: 'Safety exit started (15:10 IST)', icon: LogOut, tone: 'warning' },
  AUTO_CLOSE_POSITION_STARTED: { label: 'Auto-close position started', icon: LogOut, tone: 'warning' },
  AUTO_CLOSE_ORDER_SUBMITTED: { label: 'Auto-close order submitted', icon: ShoppingCart, tone: 'warning' },
  AUTO_CLOSE_ORDER_FILLED: { label: 'Auto-close order filled', icon: CheckCircle2, tone: 'success' },
  AUTO_CLOSE_ORDER_FAILED: { label: 'Auto-close order failed', icon: XCircle, tone: 'destructive' },
  SAFETY_EXIT_COMPLETED: { label: 'Safety exit completed', icon: ShieldCheck, tone: 'success' },
  OFFICIAL_MARKET_CLOSED: { label: 'Official market close (15:30 IST)', icon: DoorClosed, tone: 'muted' },

  // Automatic profit-target exit (TARGET-ONLY)
  TARGET_MONITOR_STARTED: { label: 'Target monitor started', icon: Target, tone: 'info' },
  TARGET_MONITOR_DEFERRED: { label: 'Target monitor deferred (awaiting fill price)', icon: Timer, tone: 'muted' },
  TARGET_MONITOR_RESUMED: { label: 'Target monitor resumed after restart', icon: RefreshCcw, tone: 'info' },
  TARGET_LTP_UNAVAILABLE: { label: 'Target LTP unavailable this tick', icon: WifiOff, tone: 'warning' },
  TARGET_HIT: { label: 'Target price hit', icon: TrendingUp, tone: 'success' },
  TARGET_EXIT_ORDER_SUBMITTED: { label: 'Target exit order submitted', icon: ShoppingCart, tone: 'warning' },
  TARGET_EXIT_ORDER_FILLED: { label: 'Target exit order filled', icon: CheckCircle2, tone: 'success' },
  TARGET_EXIT_ORDER_FAILED: { label: 'Target exit order failed', icon: XCircle, tone: 'destructive' },
  TARGET_POSITION_CLOSED: { label: 'Position closed at target', icon: CheckCircle2, tone: 'success' },
}

/** Fallback for any event type the backend adds before this map is updated - never crash the timeline. */
export const UNKNOWN_AUDIT_EVENT_META: AuditEventMeta = { label: 'Event', icon: FileSearch, tone: 'muted' }

export const AUDIT_TONE_CLASS: Record<AuditTone, string> = {
  success: 'bg-success-muted text-success',
  destructive: 'bg-destructive-muted text-destructive',
  warning: 'bg-warning-muted text-warning',
  info: 'bg-info-muted text-info',
  muted: 'bg-muted text-muted-foreground',
}
