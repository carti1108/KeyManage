// KMBurnBadge (docs/design-system.md 5.5). 카운트다운은 클립보드 소각을 맡는 쪽(COM-001)이 하고, 여기는 보여 주기만 한다
import { useEffect, useRef } from 'react'
import { cn } from '../cn'

const DEFAULT_TOTAL_SECONDS = 30

export interface BurnTimerBadgeProps {
  remainingSeconds: number
  totalSeconds?: number
  /** 0초에 닿았을 때 한 번 부른다 */
  onBurned?: () => void
  className?: string
}

export function BurnTimerBadge({
  remainingSeconds,
  totalSeconds = DEFAULT_TOTAL_SECONDS,
  onBurned,
  className,
}: BurnTimerBadgeProps) {
  const total = Number.isFinite(totalSeconds) && totalSeconds > 0 ? totalSeconds : DEFAULT_TOTAL_SECONDS
  const remaining = Number.isFinite(remainingSeconds) ? Math.min(total, Math.max(0, Math.ceil(remainingSeconds))) : 0
  const burned = remaining === 0
  const notified = useRef(false)

  useEffect(() => {
    if (!burned) {
      notified.current = false
      return
    }
    if (notified.current) return
    notified.current = true
    onBurned?.()
  }, [burned, onBurned])

  return (
    <div
      role="timer"
      aria-label={`클립보드 자동 삭제까지 ${remaining}초`}
      className={cn(
        'relative inline-flex items-center overflow-hidden rounded-full bg-muted px-3 py-1 font-mono text-xs text-muted-foreground',
        className,
      )}
    >
      <span aria-hidden>{remaining}s</span>
      <span
        aria-hidden
        className="absolute inset-x-0 top-0 h-px origin-left bg-accent transition-transform duration-1000 ease-linear"
        style={{ transform: `scaleX(${remaining / total})` }}
      />
    </div>
  )
}
