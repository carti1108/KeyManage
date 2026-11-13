import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { WebGeneratorView, WebVaultItem, WebVaultItemForm, WebVaultListState, WebVaultListView } from 'keymanage-web-session'
import { VaultListRibScreen } from './VaultListRibScreen'

// Kotlin ViewStore·ScreenSlot을 흉내 내는 가짜. 값을 바꾸고 구독자에게 알린다
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

const item = (overrides: Partial<WebVaultItem> & Pick<WebVaultItem, 'id' | 'serviceName'>): WebVaultItem =>
  ({
    username: '',
    password: '',
    url: '',
    notes: '',
    createdAt: '2026-10-31T00:00:00Z',
    updatedAt: '2026-10-31T00:00:00Z',
    ...overrides,
  }) as WebVaultItem

const APPLE = item({ id: 'ap', serviceName: 'Apple', username: 'tim', password: 'apple-secret' })
const GITHUB = item({ id: 'gh', serviceName: 'GitHub', username: 'octo', password: 'gh-secret', url: 'javascript:alert(1)', notes: '회사 계정' })

const formOf = (overrides: Partial<WebVaultItemForm> = {}) => ({ serviceName: '', username: '', password: '', url: '', notes: '', ...overrides }) as WebVaultItemForm
const EMPTY_FORM = formOf()

function listState(overrides: Partial<WebVaultListState> = {}): WebVaultListState {
  return {
    screen: 'list',
    query: '',
    items: [APPLE, GITHUB],
    isVaultEmpty: false,
    readOnly: false,
    selected: null,
    focusItemId: null,
    form: EMPTY_FORM,
    isGeneratorOpen: false,
    isConfirmingDelete: false,
    isSaving: false,
    saveError: null,
    copyError: null,
    canEdit: true,
    emptyMessage: null,
    showsFormStrength: false,
    formPasswordScore: 0,
    showsFormWeakWarning: false,
    showsFormSequentialDigitsNotice: false,
    ...overrides,
  } as WebVaultListState
}

function fakeGeneratorView() {
  return {
    store: fakeStore({
      lengthText: '16',
      uppercase: true,
      lowercase: true,
      digits: true,
      symbols: true,
      generatedText: 'generated-pw',
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
    }),
    changeLength: vi.fn(),
    setCharset: vi.fn(),
    finishEditingLength: vi.fn(),
    regenerate: vi.fn(),
    use: vi.fn(),
    close: vi.fn(),
    copy: vi.fn(),
  }
}

function setup(overrides: Partial<WebVaultListState> = {}) {
  const store = fakeStore(listState(overrides))
  const generatorView = fakeGeneratorView()
  const generatorSlot = fakeStore<{ screenKey: string; view: unknown } | null>(null)
  const view = {
    store,
    generatorSlot,
    search: vi.fn(),
    openItem: vi.fn(),
    back: vi.fn(),
    startAdd: vi.fn(),
    startEdit: vi.fn(),
    editForm: vi.fn(),
    submitForm: vi.fn(),
    cancelForm: vi.fn(),
    openGenerator: vi.fn(),
    requestDelete: vi.fn(),
    cancelDelete: vi.fn(),
    confirmDelete: vi.fn(),
    copyUsername: vi.fn(),
    copyPassword: vi.fn(),
  }
  render(<VaultListRibScreen view={view as unknown as WebVaultListView} />)
  const update = (next: Partial<WebVaultListState>) => act(() => store.set(listState({ ...store.getSnapshot(), ...next })))
  const showGenerator = () => act(() => generatorSlot.set({ screenKey: 'passwordGenerator', view: generatorView as unknown as WebGeneratorView }))
  return { view, store, update, generatorView, showGenerator, hideGenerator: () => act(() => generatorSlot.set(null)) }
}

// 목록의 항목 카드. 옆의 "… 비밀번호 복사" 버튼과 구분한다
const cardName = (name: string) => (accessibleName: string) => accessibleName.startsWith(name) && !accessibleName.endsWith('복사')

describe('VaultListRibScreen', () => {
  beforeEach(() => vi.stubGlobal('CSS', { supports: (property: string) => property === '-webkit-text-security' }))
  afterEach(() => vi.unstubAllGlobals())

  describe('목록', () => {
    it('상태가 준 순서대로 항목을 보여 준다', () => {
      setup()

      const names = within(screen.getByRole('list', { name: '항목 목록' }))
        .getAllByRole('listitem')
        .map((row) => row.textContent)
      expect(names[0]).toContain('Apple')
      expect(names[0]).toContain('tim')
      expect(names[1]).toContain('GitHub')
    })

    it('검색어를 고치면 그 글자를 보낸다', () => {
      const { view } = setup()

      fireEvent.change(screen.getByLabelText('항목 검색'), { target: { value: 'git' } })

      expect(view.search).toHaveBeenCalledWith('git')
    })

    it('상태의 검색어를 칸에 보인다', () => {
      setup({ query: 'git' })

      expect(screen.getByLabelText('항목 검색')).toHaveValue('git')
    })

    it('항목을 누르면 그 항목을 연다', () => {
      const { view } = setup()

      fireEvent.click(screen.getByRole('button', { name: cardName('GitHub') }))

      expect(view.openItem).toHaveBeenCalledWith('gh')
    })

    it('항목이 없으면 상태가 준 안내를 보인다', () => {
      setup({ items: [], isVaultEmpty: true, emptyMessage: '저장된 항목이 없습니다.' })

      expect(screen.getByText('저장된 항목이 없습니다.')).toBeInTheDocument()
      expect(screen.queryByRole('list', { name: '항목 목록' })).not.toBeInTheDocument()
    })

    it('검색 결과가 없으면 상태가 준 안내를 보인다', () => {
      setup({ items: [], emptyMessage: '검색 결과가 없습니다.' })

      expect(screen.getByText('검색 결과가 없습니다.')).toBeInTheDocument()
    })

    it('항목 추가를 누르면 추가를 시작한다', () => {
      const { view } = setup()

      fireEvent.click(screen.getByRole('button', { name: '항목 추가' }))

      expect(view.startAdd).toHaveBeenCalledOnce()
    })

    it('편집할 수 없는 볼트는 항목 추가를 보이지 않는다', () => {
      setup({ canEdit: false, readOnly: true })

      expect(screen.queryByRole('button', { name: '항목 추가' })).not.toBeInTheDocument()
    })

    it('상세를 열지 않고 목록에서 비밀번호를 복사한다', () => {
      const { view } = setup()

      fireEvent.click(screen.getByRole('button', { name: 'Apple 비밀번호 복사' }))

      expect(view.copyPassword).toHaveBeenCalledWith('ap')
      expect(view.openItem).not.toHaveBeenCalled()
    })

    it('비밀번호가 없는 항목은 목록에 복사 버튼이 없다', () => {
      setup({ items: [item({ id: 'w', serviceName: 'Wifi' })] })

      expect(screen.queryByRole('button', { name: 'Wifi 비밀번호 복사' })).not.toBeInTheDocument()
    })

    it('복사에 실패하면 상태가 준 문구를 보인다', () => {
      setup({ copyError: '클립보드에 복사하지 못했습니다.' })

      expect(screen.getByRole('alert')).toHaveTextContent('클립보드에 복사하지 못했습니다.')
    })

    it('목록으로 돌아오면 보던 항목에 포커스를 돌려준다', () => {
      const { update } = setup({ screen: 'detail', selected: GITHUB, focusItemId: 'gh' })

      update({ screen: 'list', selected: null })

      expect(screen.getByRole('button', { name: cardName('GitHub') })).toHaveFocus()
    })
  })

  describe('상세', () => {
    const detail = (overrides: Partial<WebVaultListState> = {}) => ({ screen: 'detail', selected: GITHUB, focusItemId: 'gh', ...overrides })

    it('비밀번호는 가려서 보이고 URL은 눌러서 열리는 링크로 만들지 않는다', () => {
      setup(detail())

      expect(screen.getByRole('heading', { name: 'GitHub' })).toBeInTheDocument()
      expect(screen.getByText('octo', { selector: 'dd' })).toBeInTheDocument()
      expect(screen.getByLabelText('비밀번호')).toHaveAttribute('type', 'password')
      expect(screen.getByText('javascript:alert(1)')).toBeInTheDocument()
      expect(screen.queryByRole('link')).not.toBeInTheDocument()
      expect(screen.getByText('회사 계정')).toBeInTheDocument()
    })

    it('상세를 열면 제목에 포커스를 둔다', () => {
      setup(detail())

      expect(screen.getByRole('heading', { name: 'GitHub' })).toHaveFocus()
    })

    it('아이디와 비밀번호 복사는 항목 id와 함께 보낸다', () => {
      const { view } = setup(detail())

      fireEvent.click(screen.getByRole('button', { name: '아이디 복사' }))
      fireEvent.click(screen.getByRole('button', { name: '비밀번호 복사' }))

      expect(view.copyUsername).toHaveBeenCalledWith('gh')
      expect(view.copyPassword).toHaveBeenCalledWith('gh')
    })

    it('비어 있는 아이디·URL·메모·비밀번호 복사는 그리지 않는다', () => {
      setup(detail({ selected: item({ id: 'w', serviceName: 'Wifi' }) }))

      expect(screen.queryByRole('button', { name: '아이디 복사' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '비밀번호 복사' })).not.toBeInTheDocument()
      expect(screen.queryByText('URL')).not.toBeInTheDocument()
      expect(screen.queryByText('메모')).not.toBeInTheDocument()
    })

    it('목록으로·수정을 누르면 각각 보낸다', () => {
      const { view } = setup(detail())

      fireEvent.click(screen.getByRole('button', { name: '목록으로' }))
      fireEvent.click(screen.getByRole('button', { name: '수정' }))

      expect(view.back).toHaveBeenCalledOnce()
      expect(view.startEdit).toHaveBeenCalledOnce()
    })

    it('삭제를 누르면 삭제를 요청한다', () => {
      const { view } = setup(detail())

      fireEvent.click(screen.getByRole('button', { name: '삭제' }))

      expect(view.requestDelete).toHaveBeenCalledOnce()
    })

    it('삭제 확인 창은 취소에 포커스를 두고, 확인·취소를 각각 보낸다', () => {
      const { view, update } = setup(detail())
      update({ isConfirmingDelete: true })

      const dialog = screen.getByRole('alertdialog', { name: '항목 삭제' })
      expect(dialog).toHaveAccessibleDescription('이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다.')
      expect(within(dialog).getByRole('button', { name: '취소' })).toHaveFocus()

      fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))
      fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))

      expect(view.confirmDelete).toHaveBeenCalledOnce()
      expect(view.cancelDelete).toHaveBeenCalledOnce()
    })

    it('저장 중에는 다른 화면으로 가지 못한다', () => {
      setup(detail({ isConfirmingDelete: true, isSaving: true }))

      expect(screen.getByRole('button', { name: '목록으로' })).toBeDisabled()
      expect(screen.getByRole('button', { name: '수정' })).toBeDisabled()
    })

    it('실패하면 상태가 준 제목과 이유를 보인다', () => {
      setup(detail({ saveError: { title: '저장 실패', body: '볼트 허용 용량(5MB)을 초과했거나' } as never }))

      const alert = screen.getByRole('alert')
      expect(alert).toHaveTextContent('저장 실패')
      expect(alert).toHaveTextContent('볼트 허용 용량(5MB)을 초과했거나')
    })

    it('읽기 전용 볼트는 수정·삭제를 보이지 않는다', () => {
      setup(detail({ canEdit: false, readOnly: true }))

      expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: '삭제' })).not.toBeInTheDocument()
    })
  })

  describe('추가·수정 폼', () => {
    const add = (overrides: Partial<WebVaultListState> = {}) => ({ screen: 'add', ...overrides })

    it('입력한 값을 칸 이름과 함께 보낸다', () => {
      const { view } = setup(add())

      fireEvent.change(screen.getByLabelText('서비스 이름'), { target: { value: 'Naver' } })
      fireEvent.change(screen.getByLabelText('아이디'), { target: { value: 'me' } })
      fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'pw' } })
      fireEvent.change(screen.getByLabelText('URL'), { target: { value: 'https://naver.com' } })
      fireEvent.change(screen.getByLabelText('메모'), { target: { value: '메모' } })

      expect(view.editForm.mock.calls).toEqual([
        ['serviceName', 'Naver'],
        ['username', 'me'],
        ['password', 'pw'],
        ['url', 'https://naver.com'],
        ['notes', '메모'],
      ])
    })

    it('상태의 폼 값을 채워 보인다', () => {
      setup({ screen: 'edit', selected: GITHUB, form: formOf({ serviceName: 'GitHub', username: 'octo', password: 'gh-secret' }) })

      expect(screen.getByRole('heading', { name: '항목 수정' })).toBeInTheDocument()
      expect(screen.getByLabelText('아이디')).toHaveValue('octo')
      expect(screen.getByLabelText('서비스 이름')).toHaveValue('GitHub')
    })

    it('추가 폼은 제목이 항목 추가이고 서비스 이름에 포커스를 둔다', () => {
      setup(add())

      expect(screen.getByRole('heading', { name: '항목 추가' })).toBeInTheDocument()
      expect(screen.getByLabelText('서비스 이름')).toHaveFocus()
    })

    it('저장·취소를 누르면 각각 보낸다', () => {
      const { view } = setup(add())

      fireEvent.click(screen.getByRole('button', { name: '저장' }))
      fireEvent.click(screen.getByRole('button', { name: '취소' }))

      expect(view.submitForm).toHaveBeenCalledOnce()
      expect(view.cancelForm).toHaveBeenCalledOnce()
    })

    it('저장 중에는 취소하지 못한다', () => {
      setup(add({ isSaving: true }))

      expect(screen.getByRole('button', { name: '취소' })).toBeDisabled()
    })

    it('Enter로도 저장한다', () => {
      const { view } = setup(add())

      fireEvent.keyDown(screen.getByLabelText('서비스 이름'), { key: 'Enter' })

      expect(view.submitForm).toHaveBeenCalledOnce()
    })

    it('메모의 Enter는 줄바꿈이라 저장하지 않는다', () => {
      const { view } = setup(add())

      fireEvent.keyDown(screen.getByLabelText('메모'), { key: 'Enter' })

      expect(view.submitForm).not.toHaveBeenCalled()
    })

    it('한글 조합 중의 Enter는 저장하지 않는다', () => {
      const { view } = setup(add())

      fireEvent.keyDown(screen.getByLabelText('서비스 이름'), { key: 'Enter', isComposing: true })

      expect(view.submitForm).not.toHaveBeenCalled()
    })

    it('실패하면 상태가 준 문구를 보인다', () => {
      setup(add({ saveError: { title: null, body: '서비스 이름을 입력해 주세요.' } as never }))

      expect(screen.getByRole('alert')).toHaveTextContent('서비스 이름을 입력해 주세요.')
    })

    it('브라우저가 다른 사이트 자격 증명을 이 앱의 로그인으로 저장하지 않도록 폼 제출로 다루지 않는다', () => {
      setup(add())

      const password = screen.getByLabelText('비밀번호')
      expect(password.closest('form')).toBeNull()
      expect(password).toHaveAttribute('type', 'text')
      expect(password).toHaveAttribute('autocomplete', 'off')
      expect(password).toHaveAttribute('data-1p-ignore')
      expect(password).toHaveAttribute('data-lpignore', 'true')
    })

    it('아이디·URL·메모는 맞춤법 검사로 외부에 보내지 않는다', () => {
      setup(add())

      for (const label of ['아이디', 'URL', '메모']) {
        expect(screen.getByLabelText(label)).toHaveAttribute('spellcheck', 'false')
      }
    })

    it('상태가 강도를 보이라고 할 때만 게이지를 그린다', () => {
      const { update } = setup(add())
      expect(screen.queryByRole('meter')).not.toBeInTheDocument()

      update({ showsFormStrength: true, formPasswordScore: 19 })

      expect(screen.getByRole('meter', { name: '비밀번호 강도' })).toHaveAttribute('aria-valuenow', '19')
    })

    it('약한 비밀번호 경고와 이어지는 숫자 안내는 상태가 정한 대로 보인다', () => {
      const { update } = setup(add())
      // 알림 영역은 내용이 생기기 전부터 있어서 경고가 생기면 읽힌다
      const region = screen.getByRole('status')
      expect(region).toBeEmptyDOMElement()

      update({ showsFormWeakWarning: true, showsFormSequentialDigitsNotice: true })

      expect(region).toHaveTextContent('취약한 비밀번호 경고')
      expect(region).toHaveTextContent('현재 비밀번호는 보안에 취약합니다. 80점 이상의 강력한 비밀번호로 재생성하세요.')
      expect(region).toHaveTextContent('123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다.')
    })

    it('이어지는 숫자 안내는 약한 경고 없이도 보인다', () => {
      setup(add({ showsFormSequentialDigitsNotice: true }))

      expect(screen.getByRole('status')).toHaveTextContent('123처럼 이어지는 숫자가 들어 있어 추측하기 쉽습니다.')
      expect(screen.getByRole('status')).not.toHaveTextContent('취약한 비밀번호 경고')
    })
  })

  describe('폼 안의 비밀번호 생성기', () => {
    it('비밀번호 생성을 누르면 생성기를 열어 달라고 보낸다', () => {
      const { view } = setup({ screen: 'add' })

      fireEvent.click(screen.getByRole('button', { name: '비밀번호 생성' }))

      expect(view.openGenerator).toHaveBeenCalledOnce()
    })

    it('열려 있는 동안에는 슬롯의 생성기 화면을 그리고 버튼은 감춘다', () => {
      const { update, showGenerator } = setup({ screen: 'add' })

      update({ isGeneratorOpen: true })
      showGenerator()

      expect(screen.getByTestId('generated-password')).toHaveTextContent('generated-pw')
      expect(screen.queryByRole('button', { name: '비밀번호 생성' })).not.toBeInTheDocument()
    })

    it('생성기 안에서 Enter를 눌러도 항목을 저장하지 않는다', () => {
      const { view, update, showGenerator } = setup({ screen: 'add' })
      update({ isGeneratorOpen: true })
      showGenerator()

      fireEvent.keyDown(screen.getByLabelText('길이'), { key: 'Enter' })

      expect(view.submitForm).not.toHaveBeenCalled()
    })

    it('생성기가 닫히면 비밀번호 칸으로 포커스를 돌려준다', () => {
      const { update, showGenerator, hideGenerator } = setup({ screen: 'add' })
      update({ isGeneratorOpen: true })
      showGenerator()

      update({ isGeneratorOpen: false, form: formOf({ password: 'generated-pw' }) })
      hideGenerator()

      expect(screen.getByLabelText('비밀번호')).toHaveValue('generated-pw')
      expect(screen.getByLabelText('비밀번호')).toHaveFocus()
    })
  })

  describe('데스크톱 2분할', () => {
    it('항목을 고르기 전에는 상세 자리에 안내를 보인다', () => {
      setup()

      expect(screen.getByText('선택된 항목이 없습니다')).toBeInTheDocument()
    })

    it('상세나 폼을 열면 안내를 거둔다', () => {
      setup({ screen: 'detail', selected: GITHUB })

      expect(screen.queryByText('선택된 항목이 없습니다')).not.toBeInTheDocument()
    })
  })
})
