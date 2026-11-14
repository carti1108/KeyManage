// 열린 볼트 화면의 자동 잠금 시간 선택 칸 (SEC-003). 값과 변경 알림만 받는다
import { useId } from 'react'
import { AUTO_LOCK_OPTIONS_MS } from '../../domain/lock/autoLock'

const OPTIONS: readonly { readonly ms: number; readonly label: string }[] = [
  { ms: AUTO_LOCK_OPTIONS_MS.immediately, label: '즉시' },
  { ms: AUTO_LOCK_OPTIONS_MS.oneMinute, label: '1분' },
  { ms: AUTO_LOCK_OPTIONS_MS.fiveMinutes, label: '5분' },
  { ms: AUTO_LOCK_OPTIONS_MS.fifteenMinutes, label: '15분' },
  { ms: AUTO_LOCK_OPTIONS_MS.oneHour, label: '1시간' },
]

export interface AutoLockSelectProps {
  timeoutMs: number
  onChange: (timeoutMs: number) => void
}

export function AutoLockSelect({ timeoutMs, onChange }: AutoLockSelectProps) {
  const id = useId()
  return (
    <div className="flex items-center justify-between gap-2 w-full">
      <label htmlFor={id} className="text-caption text-muted-foreground whitespace-nowrap">
        자동 잠금
      </label>
      <select
        id={id}
        value={String(timeoutMs)}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-7 rounded-md border border-input bg-background/80 px-2 text-caption outline-none focus-visible:ring-1 focus-visible:ring-ring cursor-pointer"
      >
        {OPTIONS.map(({ ms, label }) => (
          <option key={ms} value={String(ms)}>
            {label}
          </option>
        ))}
      </select>
    </div>
  )
}
