// 비밀번호 생성기 (GEN-001). 생성기 탭과 항목 폼 안의 생성기가 같은 화면을 쓴다.
// 옵션 검사·문구·사용 가능 여부는 모두 Kotlin 상태(WebGeneratorState)가 정하고, 이 화면은 그리기만 한다
import { Copy, RefreshCw } from 'lucide-react'
import { useId, type KeyboardEvent } from 'react'
import type { WebGeneratorView } from 'keymanage-web-session'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { EntropyGauge } from '../components/EntropyGauge'
import { useViewStore } from './ribsReact'

const CHARSETS = [
  ['uppercase', '대문자'],
  ['lowercase', '소문자'],
  ['digits', '숫자'],
  ['symbols', '특수기호'],
] as const

// 바깥 항목 폼은 Enter를 저장으로 처리한다. 생성기 안의 Enter는 저장으로 넘기지 않는다
function stopEnter(event: KeyboardEvent) {
  if (event.key === 'Enter') event.stopPropagation()
}

export function GeneratorRibScreen({ view }: { view: WebGeneratorView }) {
  const state = useViewStore(view.store)
  const lengthId = useId()

  return (
    <div className="flex flex-col gap-3 rounded-md border border-border bg-muted/40 p-3" onKeyDown={stopEnter}>
      <p role="status" className="sr-only">
        {state.announcement}
      </p>
      {/* 옵션이 잘못된 동안에는 옵션과 맞지 않는 이전 결과를 보이지 않는다(상태가 null을 준다) */}
      {state.generatedText != null && (
        <>
          <p data-testid="generated-password" className="break-all font-mono text-sm">
            {state.generatedText}
          </p>
          <EntropyGauge score={state.generatedScore} label="생성된 비밀번호 강도" />
        </>
      )}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <label htmlFor={lengthId} className="flex items-center gap-2">
          길이
          <input
            id={lengthId}
            type="number"
            min={8}
            max={64}
            value={state.lengthText}
            autoFocus
            onChange={(e) => view.changeLength(e.target.value)}
            onBlur={() => view.finishEditingLength()}
            className="h-8 w-16 rounded-md border border-input bg-transparent px-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </label>
        {CHARSETS.map(([key, label]) => (
          <label key={key} className="flex items-center gap-1.5">
            <input type="checkbox" checked={state[key]} onChange={(e) => view.setCharset(key, e.target.checked)} />
            {label}
          </label>
        ))}
      </div>
      {state.problemMessage != null && <Alert>{state.problemMessage}</Alert>}
      {state.copyError != null && <Alert>{state.copyError}</Alert>}
      <div className="flex flex-wrap justify-end gap-2">
        {state.showsClose && (
          <Button variant="ghost" size="sm" onClick={() => view.close()}>
            닫기
          </Button>
        )}
        <Button variant="outline" size="sm" icon={<RefreshCw aria-hidden className="size-4" />} onClick={() => view.regenerate()}>
          다시 생성
        </Button>
        {state.showsCopy && (
          <Button variant="outline" size="sm" icon={<Copy aria-hidden className="size-4" />} onClick={() => view.copy()} disabled={!state.canUseGenerated}>
            복사
          </Button>
        )}
        {state.showsUse && (
          <Button size="sm" onClick={() => view.use()} disabled={!state.canUseGenerated}>
            이 비밀번호 사용
          </Button>
        )}
      </div>
    </div>
  )
}
