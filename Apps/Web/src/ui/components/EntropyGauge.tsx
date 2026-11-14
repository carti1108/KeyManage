// KMGaugeBar (docs/design-system.md 5.4). 경계값은 GEN-001: 60점 미만 취약, 80점 미만 보통
import { cn } from '../cn'

const MAX_SCORE = 100

function levelOf(score: number) {
  if (score < 60) return { label: '취약함', bar: 'bg-destructive', text: 'text-destructive' }
  if (score < 80) return { label: '보통', bar: 'bg-primary', text: 'text-primary' }
  return { label: '초강력', bar: 'bg-accent', text: 'text-accent' }
}

function clampScore(score: number): number {
  if (!Number.isFinite(score)) return 0
  return Math.min(MAX_SCORE, Math.max(0, Math.floor(score)))
}

export interface EntropyGaugeProps {
  /** 0~100. 범위를 벗어나면 맞추고 소수점은 버린다 */
  score: number
  showLabel?: boolean
  animated?: boolean
  /** 한 화면에 게이지가 둘 이상이면 보조 기술이 구분하도록 바꾼다 */
  label?: string
  className?: string
}

export function EntropyGauge({ score, showLabel = true, animated = true, label = '비밀번호 강도', className }: EntropyGaugeProps) {
  const value = clampScore(score)
  const level = levelOf(value)

  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={MAX_SCORE}
        aria-valuenow={value}
        aria-valuetext={`${value}점, ${level.label}`}
        className="h-2 flex-1 overflow-hidden rounded-full bg-muted"
      >
        <div
          className={cn('h-full rounded-full', level.bar, animated && 'transition-[width] duration-300 ease-out')}
          style={{ width: `${value}%` }}
        />
      </div>
      {showLabel && (
        <span aria-hidden className={cn('text-label font-medium', level.text)}>
          {level.label}
        </span>
      )}
    </div>
  )
}
