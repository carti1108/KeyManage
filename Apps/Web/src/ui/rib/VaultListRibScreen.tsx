// 볼트 목록·상세·추가·수정 화면 (WebVaultListView). 와이어프레임 03 볼트 / 04 편집의 데스크톱 2분할을 그린다.
// 검색어·폼 값·확인 창·저장 상태·안내 문구는 모두 Kotlin 상태(WebVaultListState)가 들고, 이 화면은 그리기와 입력 전달만 한다
import { ArrowLeft, Copy, Pencil, Plus, Shield, Trash2, Wand2 } from 'lucide-react'
import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode, type RefObject } from 'react'
import type { WebVaultItem, WebVaultListState, WebVaultListView } from 'keymanage-web-session'
import { cn } from '../cn'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ConfirmPrompt } from '../components/ConfirmPrompt'
import { EntropyGauge } from '../components/EntropyGauge'
import { PasswordField } from '../components/PasswordField'
import { TextField } from '../components/TextField'
import { GeneratorRibScreen } from './GeneratorRibScreen'
import { RibSlot, useViewStore } from './ribsReact'

// 이 폼의 값은 다른 사이트의 자격 증명과 비밀 메모다. 맞춤법 검사·자동 고침은 입력값을 외부 서비스로 보낼 수 있다
const PRIVATE_TEXT = { spellCheck: false, autoCorrect: 'off', autoCapitalize: 'off', autoComplete: 'off' } as const
// 비밀번호 관리자 확장이 이 칸을 채우거나 저장하지 않게 한다
const IGNORE_PASSWORD_MANAGERS = { 'data-1p-ignore': true, 'data-lpignore': 'true', 'data-bwignore': true } as const

const noop = () => {}

// 자식 RIB(비밀번호 생성기)가 들어올 화면들
const GENERATOR_SCREENS = { passwordGenerator: (view: Parameters<typeof GeneratorRibScreen>[0]['view']) => <GeneratorRibScreen view={view} /> }

interface PaneProps {
  view: WebVaultListView
  state: WebVaultListState
}

export function VaultListRibScreen({ view }: { view: WebVaultListView }) {
  const state = useViewStore(view.store)
  // 상세·수정은 고른 항목이 있을 때만 연다
  const isPanelActive = state.screen === 'add' || ((state.screen === 'detail' || state.screen === 'edit') && state.selected != null)

  return (
    <>
      {state.copyError != null && <Alert>{state.copyError}</Alert>}
      <div className="flex flex-col lg:flex-row gap-6 w-full items-start">
        {/* 좌측 목록 컬럼 (와이어프레임 Web: 360px / lg:w-96) */}
        <div className={cn('w-full lg:w-96 lg:shrink-0', isPanelActive && 'hidden lg:block')}>
          <ListPane view={view} state={state} />
        </div>
        {/* 우측 상세/편집 컬럼 (와이어프레임 Web: flex-1) */}
        <div className={cn('w-full flex-1 min-w-0', !isPanelActive && 'hidden lg:block')}>
          <DetailPane view={view} state={state} />
        </div>
      </div>
    </>
  )
}

function ListPane({ view, state }: PaneProps) {
  const returnTarget = useRef<HTMLButtonElement>(null)

  // 목록이 다시 보일 때(상세에서 돌아옴) 보던 항목으로 포커스를 돌려준다. 검색하는 동안에는 빼앗지 않는다
  useEffect(() => {
    if (state.screen === 'list') returnTarget.current?.focus()
  }, [state.screen, state.focusItemId])

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-end gap-2">
        <TextField
          label="항목 검색"
          type="search"
          autoComplete="off"
          value={state.query}
          onChange={(e) => view.search(e.target.value)}
          className="flex-1"
        />
        {state.canEdit && (
          <Button icon={<Plus aria-hidden className="size-4" />} onClick={() => view.startAdd()}>
            항목 추가
          </Button>
        )}
      </div>
      {state.items.length === 0 ? (
        state.emptyMessage != null && <p className="py-8 text-center text-sm text-muted-foreground">{state.emptyMessage}</p>
      ) : (
        <ul aria-label="항목 목록" className="flex flex-col gap-2">
          {state.items.map((item) => (
            // 누를 수 있는 카드(button) 안에는 버튼을 둘 수 없어 복사 버튼은 카드 옆에 둔다
            <li key={item.id} className="flex items-center gap-2">
              <Card
                interactive
                padding="sm"
                className="min-w-0 flex-1"
                onClick={() => view.openItem(item.id)}
                ref={item.id === state.focusItemId ? returnTarget : undefined}
              >
                <span className="block font-medium">{item.serviceName}</span>
                {item.username !== '' && <span className="block truncate text-sm text-muted-foreground">{item.username}</span>}
              </Card>
              {item.password !== '' && (
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`${item.serviceName} 비밀번호 복사`}
                  icon={<Copy aria-hidden className="size-4" />}
                  onClick={() => view.copyPassword(item.id)}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function DetailPane({ view, state }: PaneProps) {
  if (state.screen === 'add') return <ItemFormPane view={view} state={state} />
  if (state.selected != null && state.screen === 'edit') return <ItemFormPane view={view} state={state} />
  if (state.selected != null && state.screen === 'detail') {
    return <ItemDetailPane key={state.selected.id} view={view} state={state} item={state.selected} />
  }

  // 데스크톱에서 아직 항목을 선택하지 않았을 때의 빈 상태 안내
  return (
    <div className="flex h-64 flex-col items-center justify-center rounded-lg border border-dashed border-border p-8 text-center text-muted-foreground">
      <Shield aria-hidden className="mb-2 size-8 text-muted-foreground/40" />
      <p className="text-sm font-medium">선택된 항목이 없습니다</p>
      <p className="text-xs text-muted-foreground">목록에서 항목을 선택하거나 새 항목을 추가해 주세요.</p>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words text-sm">{children}</dd>
    </div>
  )
}

function ItemDetailPane({ view, state, item }: PaneProps & { item: WebVaultItem }) {
  const heading = useRef<HTMLHeadingElement>(null)

  // 화면이 바뀌면 누른 버튼이 사라지므로, 보조 기술이 새 화면을 알도록 제목으로 포커스를 옮긴다
  useEffect(() => heading.current?.focus(), [])

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        {/* 삭제 요청 중에 화면을 떠나면 결과(실패 안내)를 볼 곳이 없어진다 */}
        <Button variant="ghost" size="sm" icon={<ArrowLeft aria-hidden className="size-4" />} onClick={() => view.back()} disabled={state.isSaving}>
          목록으로
        </Button>
        {state.canEdit && (
          <div className="flex gap-2">
            <Button variant="outline" size="sm" icon={<Pencil aria-hidden className="size-4" />} onClick={() => view.startEdit()} disabled={state.isSaving}>
              수정
            </Button>
            <Button
              variant="destructive"
              size="sm"
              icon={<Trash2 aria-hidden className="size-4" />}
              onClick={() => view.requestDelete()}
              disabled={state.isSaving}
            >
              삭제
            </Button>
          </div>
        )}
      </div>
      {state.isConfirmingDelete && (
        <ConfirmPrompt
          title={state.deleteConfirm.title}
          body={state.deleteConfirm.body}
          confirmLabel={state.deleteConfirm.confirm}
          cancelLabel={state.deleteConfirm.cancel}
          pending={state.isSaving}
          onConfirm={() => view.confirmDelete()}
          onCancel={() => view.cancelDelete()}
        />
      )}
      {state.saveError != null && <Alert title={state.saveError.title ?? undefined}>{state.saveError.body}</Alert>}
      <Card className="flex flex-col gap-4">
        <h2 ref={heading} tabIndex={-1} className="text-lg font-semibold outline-none">
          {item.serviceName}
        </h2>
        {item.username !== '' && (
          <div className="flex items-end justify-between gap-2">
            <dl className="min-w-0">
              <Field label="아이디">{item.username}</Field>
            </dl>
            <Button variant="ghost" size="sm" aria-label="아이디 복사" icon={<Copy aria-hidden className="size-4" />} onClick={() => view.copyUsername(item.id)} />
          </div>
        )}
        {/* 비밀번호는 가려서 보이고, 보기를 누르면 10초 동안만 펼친다 */}
        <PasswordField
          label="비밀번호"
          value={item.password}
          onTextChange={noop}
          readOnly
          placeholder=""
          onCopy={item.password !== '' ? () => view.copyPassword(item.id) : undefined}
        />
        {(item.url !== '' || item.notes !== '') && (
          <dl className="flex flex-col gap-3">
            {/* 저장된 URL을 링크로 만들면 javascript: 같은 값이 실행될 수 있어 글자로만 보인다 */}
            {item.url !== '' && <Field label="URL">{item.url}</Field>}
            {item.notes !== '' && (
              <Field label="메모">
                <span className="whitespace-pre-wrap">{item.notes}</span>
              </Field>
            )}
          </dl>
        )}
      </Card>
    </section>
  )
}

// 생성기가 닫히면 누른 버튼도 사라지므로 비밀번호 칸으로 포커스를 돌려준다
function useRestoreFocusWhenClosed(isOpen: boolean, target: RefObject<HTMLInputElement | null>) {
  const wasOpen = useRef(false)
  useEffect(() => {
    if (wasOpen.current && !isOpen) target.current?.focus()
    wasOpen.current = isOpen
  }, [isOpen, target])
}

function ItemFormPane({ view, state }: PaneProps) {
  const { form } = state
  const passwordInput = useRef<HTMLInputElement>(null)
  const titleId = useId()
  const notesId = useId()
  useRestoreFocusWhenClosed(state.isGeneratorOpen, passwordInput)

  // <form> 제출로 다루면 브라우저가 아이디·비밀번호 쌍을 이 앱의 로그인 정보로 저장하려 한다. Enter는 직접 처리한다
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
    if (event.target instanceof HTMLTextAreaElement || event.target instanceof HTMLButtonElement) return
    event.preventDefault()
    view.submitForm()
  }

  return (
    <Card padding="lg">
      <div role="group" aria-labelledby={titleId} className="flex flex-col gap-4" onKeyDown={onKeyDown}>
        <h2 id={titleId} className="text-lg font-semibold">
          {state.screen === 'edit' ? '항목 수정' : '항목 추가'}
        </h2>
        <TextField label="서비스 이름" value={form.serviceName} onChange={(e) => view.editForm('serviceName', e.target.value)} required autoFocus />
        <TextField label="아이디" value={form.username} onChange={(e) => view.editForm('username', e.target.value)} {...PRIVATE_TEXT} />
        <PasswordField
          label="비밀번호"
          value={form.password}
          onTextChange={(value) => view.editForm('password', value)}
          autoComplete="off"
          concealment="css"
          ref={passwordInput}
          {...IGNORE_PASSWORD_MANAGERS}
        />
        {state.showsFormStrength && <EntropyGauge score={state.formPasswordScore} />}
        {/* 약한 비밀번호도 저장은 허용한다. 사이트마다 규칙이 달라 사용자가 고를 수 없는 경우가 있다 (GEN-001) */}
        {/* 알림 영역은 늘 두고 안의 내용만 바꾼다. 내용과 함께 새로 생긴 영역은 화면 낭독기가 읽지 않는 경우가 있다 */}
        <div role="status">
          {state.showsFormWeakWarning && (
            <Alert variant="info" title={state.weakPasswordWarning.title ?? undefined} live={false}>
              {state.weakPasswordWarning.body}
            </Alert>
          )}
          {/* 점수와 별개로 안내만 한다. 점수가 높아도 보인다 (GEN-001) */}
          {state.showsFormSequentialDigitsNotice && (
            <p className="mt-1 text-sm text-muted-foreground">{state.sequentialDigitsNotice}</p>
          )}
        </div>
        {state.isGeneratorOpen ? (
          <RibSlot slot={view.generatorSlot} screens={GENERATOR_SCREENS} />
        ) : (
          <Button variant="outline" size="sm" icon={<Wand2 aria-hidden className="size-4" />} onClick={() => view.openGenerator()} className="self-start">
            비밀번호 생성
          </Button>
        )}
        <TextField label="URL" type="url" value={form.url} onChange={(e) => view.editForm('url', e.target.value)} {...PRIVATE_TEXT} />
        <div className="flex flex-col gap-1.5">
          <label htmlFor={notesId} className="text-sm font-medium">
            메모
          </label>
          <textarea
            id={notesId}
            value={form.notes}
            onChange={(e) => view.editForm('notes', e.target.value)}
            rows={3}
            {...PRIVATE_TEXT}
            className="rounded-md border border-input bg-transparent px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
          />
        </div>
        {state.saveError != null && <Alert title={state.saveError.title ?? undefined}>{state.saveError.body}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => view.cancelForm()} disabled={state.isSaving}>
            취소
          </Button>
          <Button onClick={() => view.submitForm()} loading={state.isSaving}>
            저장
          </Button>
        </div>
      </div>
    </Card>
  )
}
