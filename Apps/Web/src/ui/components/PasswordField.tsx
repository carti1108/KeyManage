// KMPasswordField (docs/design-system.md 5.3)
import { Copy, Eye, EyeOff } from 'lucide-react'
import { useEffect, useId, useState, type ComponentProps } from 'react'
import { cn } from '../cn'

/** 평문으로 둔 채 조작이 없으면 다시 가리기까지의 시간 */
export const REMASK_AFTER_MS = 10_000

function supportsTextSecurity(): boolean {
  return typeof CSS !== 'undefined' && CSS.supports?.('-webkit-text-security', 'disc') === true
}

export interface PasswordFieldProps extends Omit<ComponentProps<'input'>, 'type' | 'value' | 'onChange'> {
  /** 화면에 보이는 이름이자 접근성 이름 */
  label: string
  value: string
  onTextChange: (value: string) => void
  showCopyAction?: boolean
  /** 복사 버튼을 눌렀을 때. 클립보드 쓰기와 소각은 받는 쪽이 맡는다 (COM-001) */
  onCopy?: () => void
  /**
   * native: type=password로 가린다(기본).
   * css: text 칸을 CSS로 가린다. 다른 사이트 비밀번호를 입력하는 칸에서 브라우저가 이 앱의 로그인으로 저장을 제안하지 않게 한다.
   * 지원하지 않는 브라우저는 평문이 보이지 않도록 native로 돌아간다
   */
  concealment?: 'native' | 'css'
}

export function PasswordField({
  label,
  value,
  onTextChange,
  showCopyAction = true,
  onCopy,
  concealment = 'native',
  placeholder = '비밀번호 입력',
  autoComplete,
  id,
  className,
  ...props
}: PasswordFieldProps) {
  const fallbackId = useId()
  const inputId = id ?? fallbackId
  const [revealed, setRevealed] = useState(false)
  const cssConcealment = concealment === 'css' && supportsTextSecurity()
  const masked = !revealed

  // 입력이나 생성기 재생성으로 값이 바뀌면 다시 가리기 타이머를 처음부터 다시 잰다
  useEffect(() => {
    if (!revealed) return
    const timerId = window.setTimeout(() => setRevealed(false), REMASK_AFTER_MS)
    return () => window.clearTimeout(timerId)
  }, [revealed, value])

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
          type={revealed || cssConcealment ? 'text' : 'password'}
          // 가리는 규칙은 index.css의 input[data-masked='css']
          data-masked={cssConcealment && masked ? 'css' : undefined}
          value={value}
          onChange={(e) => onTextChange(e.target.value)}
          placeholder={placeholder}
          className="h-full min-w-0 flex-1 bg-transparent px-3 font-mono text-sm outline-none placeholder:font-sans placeholder:text-muted-foreground"
          {...props}
          // 평문(type=text)일 때 제출하면 브라우저 입력 기록에 남으므로 자동완성을 끈다
          autoComplete={revealed ? 'off' : autoComplete}
          // text 칸은 가려져 있어도 복사하면 평문이 나온다. password 칸처럼 막는다
          onCopy={cssConcealment && masked ? (e) => e.preventDefault() : undefined}
          onCut={cssConcealment && masked ? (e) => e.preventDefault() : undefined}
          // 맞춤법 검사·자동 고침은 입력값을 외부 서비스로 보낼 수 있어 호출하는 쪽이 켜지 못하게 마지막에 둔다
          spellCheck={false}
          autoCapitalize="off"
          autoCorrect="off"
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
        {showCopyAction && onCopy && (
          <button type="button" aria-label="비밀번호 복사" onClick={onCopy} className={iconButton}>
            <Copy aria-hidden className="size-4" />
          </button>
        )}
      </div>
    </div>
  )
}
