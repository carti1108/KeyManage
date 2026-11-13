import { fireEvent, render, screen, act } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { WebGeneratorState, WebGeneratorView } from 'keymanage-web-session'
import { GeneratorRibScreen } from './GeneratorRibScreen'

// Kotlin ViewStore를 흉내 내는 가짜. 상태를 바꾸고 구독자에게 알린다
function fakeStore<S>(initial: S) {
  let state = initial
  const listeners = new Set<() => void>()
  return {
    subscribe: (listener: () => void) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    getSnapshot: () => state,
    set(next: S) {
      state = next
      listeners.forEach((listener) => listener())
    },
  }
}

function generatorState(overrides: Partial<WebGeneratorState> = {}): WebGeneratorState {
  return {
    lengthText: '16',
    uppercase: true,
    lowercase: true,
    digits: true,
    symbols: true,
    generatedText: 'pw1-16',
    generatedScore: 90,
    generatedIsWeak: false,
    generatedHasSequentialDigits: false,
    problemMessage: null,
    announcement: '',
    copyError: null,
    showsUse: true,
    showsClose: true,
    showsCopy: false,
    canUseGenerated: true,
    ...overrides,
  } as WebGeneratorState
}

function setup(overrides: Partial<WebGeneratorState> = {}) {
  const store = fakeStore(generatorState(overrides))
  const view = {
    store,
    changeLength: vi.fn(),
    setCharset: vi.fn(),
    finishEditingLength: vi.fn(),
    regenerate: vi.fn(),
    use: vi.fn(),
    close: vi.fn(),
    copy: vi.fn(),
  }
  render(<GeneratorRibScreen view={view as unknown as WebGeneratorView} />)
  return { view, store }
}

describe('GeneratorRibScreen', () => {
  it('상태에 담긴 비밀번호와 옵션을 그린다', () => {
    setup()

    expect(screen.getByTestId('generated-password')).toHaveTextContent('pw1-16')
    expect(screen.getByLabelText('길이')).toHaveValue(16)
    for (const label of ['대문자', '소문자', '숫자', '특수기호']) expect(screen.getByLabelText(label)).toBeChecked()
  })

  it('꺼진 문자군은 체크하지 않는다', () => {
    setup({ symbols: false })

    expect(screen.getByLabelText('특수기호')).not.toBeChecked()
    expect(screen.getByLabelText('대문자')).toBeChecked()
  })

  it('길이를 고치면 그 글자를 그대로 보낸다', () => {
    const { view } = setup()

    fireEvent.change(screen.getByLabelText('길이'), { target: { value: '20' } })

    expect(view.changeLength).toHaveBeenCalledWith('20')
  })

  it('문자군을 누르면 바뀐 값을 보낸다', () => {
    const { view } = setup()

    fireEvent.click(screen.getByLabelText('특수기호'))

    expect(view.setCharset).toHaveBeenCalledWith('symbols', false)
  })

  it('길이 칸을 벗어나면 입력을 마쳤다고 알린다', () => {
    const { view } = setup()

    fireEvent.blur(screen.getByLabelText('길이'))

    expect(view.finishEditingLength).toHaveBeenCalledOnce()
  })

  it('다시 생성을 누르면 다시 만들어 달라고 보낸다', () => {
    const { view } = setup()

    fireEvent.click(screen.getByRole('button', { name: '다시 생성' }))

    expect(view.regenerate).toHaveBeenCalledOnce()
  })

  it('사용을 누르면 사용을 보낸다', () => {
    const { view } = setup()

    fireEvent.click(screen.getByRole('button', { name: '이 비밀번호 사용' }))

    expect(view.use).toHaveBeenCalledOnce()
  })

  it('길이가 잘못되면 사용을 막고 이전 결과와 강도를 보이지 않는다', () => {
    setup({ generatedText: null, generatedScore: 0, canUseGenerated: false, problemMessage: '비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.' })

    expect(screen.getByRole('alert')).toHaveTextContent('비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.')
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeDisabled()
    expect(screen.queryByTestId('generated-password')).not.toBeInTheDocument()
    expect(screen.queryByRole('meter')).not.toBeInTheDocument()
  })

  it('문자 종류를 모두 끄면 정책 문구로 알리고 사용을 막으며 이전 결과를 보이지 않는다', () => {
    setup({
      uppercase: false,
      lowercase: false,
      digits: false,
      symbols: false,
      generatedText: null,
      generatedScore: 0,
      canUseGenerated: false,
      problemMessage: '최소 1개 이상의 문자 종류를 선택해야 합니다.',
    })

    expect(screen.getByRole('alert')).toHaveTextContent('최소 1개 이상의 문자 종류를 선택해야 합니다.')
    expect(screen.getByRole('button', { name: '이 비밀번호 사용' })).toBeDisabled()
    expect(screen.queryByTestId('generated-password')).not.toBeInTheDocument()
    for (const label of ['대문자', '소문자', '숫자', '특수기호']) expect(screen.getByLabelText(label)).not.toBeChecked()
  })

  it('강도를 항목 비밀번호와 구분되는 이름으로 보여 준다', () => {
    setup({ generatedScore: 72 })

    const meter = screen.getByRole('meter', { name: '생성된 비밀번호 강도' })
    expect(meter).toHaveAttribute('aria-valuenow', '72')
  })

  it('열면 길이 칸에 포커스를 둔다', () => {
    setup()

    expect(screen.getByLabelText('길이')).toHaveFocus()
  })

  it('새로 만들면 보조 기술이 읽을 안내를 상태에서 받아 보인다', () => {
    const { store } = setup()
    expect(screen.getByRole('status')).toHaveTextContent('')

    act(() => store.set(generatorState({ announcement: '새 비밀번호를 만들었습니다.' })))

    expect(screen.getByRole('status')).toHaveTextContent('새 비밀번호를 만들었습니다.')
  })

  it('상태가 바뀌면 다시 그린다', () => {
    const { store } = setup()

    act(() => store.set(generatorState({ generatedText: 'pw2-20', lengthText: '20' })))

    expect(screen.getByTestId('generated-password')).toHaveTextContent('pw2-20')
    expect(screen.getByLabelText('길이')).toHaveValue(20)
  })

  it('닫기 버튼은 보이라고 할 때만 두고, 누르면 닫기를 보낸다', () => {
    const { view } = setup()

    fireEvent.click(screen.getByRole('button', { name: '닫기' }))

    expect(view.close).toHaveBeenCalledOnce()
  })

  it('닫기·사용을 보이지 않을 때(생성기 탭)는 복사만 둔다', () => {
    const { view } = setup({ showsClose: false, showsUse: false, showsCopy: true })

    expect(screen.queryByRole('button', { name: '닫기' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '이 비밀번호 사용' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '복사' }))
    expect(view.copy).toHaveBeenCalledOnce()
  })

  it('옵션이 잘못되면 복사도 막는다', () => {
    setup({ showsClose: false, showsUse: false, showsCopy: true, canUseGenerated: false, generatedText: null })

    expect(screen.getByRole('button', { name: '복사' })).toBeDisabled()
  })

  it('복사 실패 문구를 보인다', () => {
    setup({ showsCopy: true, copyError: '클립보드에 복사하지 못했습니다.' })

    expect(screen.getByRole('alert')).toHaveTextContent('클립보드에 복사하지 못했습니다.')
  })

  it('안에서 누른 Enter는 바깥 폼의 저장으로 넘기지 않는다', () => {
    const outer = vi.fn()
    const view = { store: fakeStore(generatorState()), changeLength: vi.fn(), setCharset: vi.fn(), finishEditingLength: vi.fn(), regenerate: vi.fn(), use: vi.fn(), close: vi.fn(), copy: vi.fn() }
    render(
      <div onKeyDown={outer}>
        <GeneratorRibScreen view={view as unknown as WebGeneratorView} />
      </div>,
    )

    fireEvent.keyDown(screen.getByLabelText('길이'), { key: 'Enter' })
    expect(outer).not.toHaveBeenCalled()

    fireEvent.keyDown(screen.getByLabelText('길이'), { key: 'a' })
    expect(outer).toHaveBeenCalledOnce()
  })
})
