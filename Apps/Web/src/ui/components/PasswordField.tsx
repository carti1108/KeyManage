// KMPasswordField (docs/design-system.md 5.3)
import { Copy, Eye, EyeOff } from 'lucide-react'
import { useEffect, useId, useState, type ComponentProps } from 'react'
import { cn } from '../cn'

/** 평문으로 둔 채 조작이 없으면 다시 가리기까지의 시간 */
export const REMASK_AFTER_MS = 10_000

export interface PasswordFieldProps extends Omit<ComponentProps<'input'>, 'type' | 'value' | 'onChange'> {
  /** 화면에 보이는 이름이자 접근성 이름 */
  label: string
  value: string
  onTextChange: (value: string) => void
  showCopyAction?: boolean
  /** 복사 버튼을 눌렀을 때. 클립보드 쓰기와 소각은 받는 쪽이 맡는다 (COM-001) */
  onCopy?: () => void
}

export function PasswordField({
  label,
  value,
  onTextChange,
  showCopyAction = true,
  onCopy,
  placeholder = '비밀번호 입력',
  id,
  className,
  ...props
}: PasswordFieldProps) {
  const fallbackId = useId()
  const inputId = id ?? fallbackId
  const [revealed, setRevealed] = useState(false)
  // 입력할 때마다 바뀌어 다시 가리기 타이머를 처음부터 다시 잰다
  const [lastInteraction, setLastInteraction] = useState(0)

  useEffect(() => {
    if (!revealed) return
    const id = window.setTimeout(() => setRevealed(false), REMASK_AFTER_MS)
    return () => window.clearTimeout(id)
  }, [revealed, lastInteraction])

  const iconButton =
    'inline-flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring'

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <div className="flex h-10 items-center gap-1 rounded-md border border-input bg-transparent pr-1 focus-within:ring-2 focus-within:ring-ring">
        <input
          id={inputId}
          type={revealed ? 'text' : 'password'}
          value={value}
          onChange={(e) => {
            setLastInteraction((n) => n + 1)
            onTextChange(e.target.value)
          }}
          placeholder={placeholder}
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
          className="h-full min-w-0 flex-1 bg-transparent px-3 font-mono text-sm outline-none placeholder:font-sans placeholder:text-muted-foreground"
          {...props}
        />
        <button
          type="button"
          aria-label={revealed ? '비밀번호 숨기기' : '비밀번호 보기'}
          aria-controls={inputId}
          onClick={() => setRevealed((r) => !r)}
          className={iconButton}
        >
          {revealed ? <EyeOff aria-hidden className="size-4" /> : <Eye aria-hidden className="size-4" />}
        </button>
        {showCopyAction && (
          <button type="button" aria-label="비밀번호 복사" onClick={onCopy} className={iconButton}>
            <Copy aria-hidden className="size-4" />
          </button>
        )}
      </div>
    </div>
  )
}
