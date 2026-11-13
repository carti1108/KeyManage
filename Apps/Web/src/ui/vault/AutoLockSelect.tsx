// 열린 볼트 화면의 자동 잠금 시간 선택 칸 (SEC-003). 선택지·값·변경 알림만 받는다
import { useId } from 'react'
import { AUTO_LOCK_OPTIONS_MS } from '../../domain/lock/autoLock'

const SECOND_MS = 1_000
const MINUTE_MS = 60 * SECOND_MS
const HOUR_MS = 60 * MINUTE_MS

/** 선택지를 읽는 글자. 0은 "즉시", 한 시간 단위로 떨어지면 "N시간", 아니면 "N분" */
function optionLabel(ms: number): string {
  if (ms === 0) return '즉시'
  if (ms % HOUR_MS === 0) return `${ms / HOUR_MS}시간`
  if (ms % MINUTE_MS === 0) return `${ms / MINUTE_MS}분`
  return `${ms / SECOND_MS}초`
}

/** 선택지를 따로 받지 않는 호출자(옛 화면)를 위한 기본값 */
const DEFAULT_OPTIONS_MS: readonly number[] = Object.values(AUTO_LOCK_OPTIONS_MS)

export interface AutoLockSelectProps {
  timeoutMs: number
  /** 고를 수 있는 자동 잠금 시간(ms). 상태(autoLockOptionsMs)가 준 값을 그대로 그린다 */
  optionsMs?: readonly number[]
  onChange: (timeoutMs: number) => void
}

export function AutoLockSelect({ timeoutMs, optionsMs = DEFAULT_OPTIONS_MS, onChange }: AutoLockSelectProps) {
  const id = useId()
  return (
    <div className="flex items-center justify-between gap-2 w-full">
      <label htmlFor={id} className="text-xs text-muted-foreground whitespace-nowrap">
        자동 잠금
      </label>
      <select
        id={id}
        value={String(timeoutMs)}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-7 rounded-md border border-input bg-background/80 px-2 text-xs outline-none focus-visible:ring-1 focus-visible:ring-ring cursor-pointer"
      >
        {optionsMs.map((ms) => (
          <option key={ms} value={String(ms)}>
            {optionLabel(ms)}
          </option>
        ))}
      </select>
    </div>
  )
}
