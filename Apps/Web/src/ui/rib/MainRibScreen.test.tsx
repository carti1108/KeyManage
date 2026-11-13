import { act, fireEvent, render, screen, within } from '@testing-library/react'
import type { WebConfirmText, WebMainState, WebMainView, WebReconnectPrompt } from 'keymanage-web-session'
import { describe, expect, it, vi } from 'vitest'
import { MainRibScreen } from './MainRibScreen'
import { asState, PENDING_LOGOUT_TEXT, RECONNECT_PROMPT_TEXT, ROLLBACK_WARNING_TEXT } from './policyTextFixtures'

// 탭 안쪽 화면은 다른 담당 파일이므로 가짜로 바꿔 이 화면만 시험한다
vi.mock('./VaultListRibScreen', () => ({ VaultListRibScreen: () => <div>볼트 목록 화면</div> }))
vi.mock('./GeneratorRibScreen', () => ({ GeneratorRibScreen: () => <div>생성기 화면</div> }))
vi.mock('./SettingsRibScreen', () => ({ SettingsRibScreen: () => <div>설정 화면</div> }))

type Plain = Omit<WebMainState, 'copy' | 'hashCode' | 'equals'>

const BASE: Plain = {
  email: 'user@example.com',
  selectedTab: 'vault',
  readOnly: false,
  offline: false,
  syncError: null,
  showsDeviceStorageWarning: false,
  needsReconnect: false,
  reconnectPassword: '',
  isReconnecting: false,
  reconnectError: null,
  isConfirmingReconnectRollback: false,
  isConfirmingLogout: false,
  isLoggingOut: false,
  offlineNotice: null,
  readOnlyNotice: null,
  deviceStorageWarning: null,
  pendingLogoutConfirm: PENDING_LOGOUT_TEXT,
  reconnectPrompt: RECONNECT_PROMPT_TEXT,
  rollbackWarning: ROLLBACK_WARNING_TEXT,
}

function message(title: string, body: string): Plain['syncError'] {
  return { title, body } as unknown as Plain['syncError']
}

function fakeStore<S>(initial: S) {
  let snapshot = initial
  const listeners = new Set<() => void>()
  return {
    subscribe: (listener: () => void) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    getSnapshot: () => snapshot,
    set(next: S) {
      snapshot = next
      listeners.forEach((listener) => listener())
    },
  }
}

function setup(overrides: Partial<Plain> = {}, screenKey: string | null = 'vaultList') {
  const store = fakeStore<Plain>({ ...BASE, ...overrides })
  const slot = fakeStore<{ screenKey: string; view: unknown } | null>(screenKey === null ? null : { screenKey, view: {} })
  const view = {
    store,
    slot,
    selectTab: vi.fn(),
    requestLock: vi.fn(),
    requestLogout: vi.fn(),
    confirmLogout: vi.fn(),
    cancelLogout: vi.fn(),
    updateReconnectPassword: vi.fn(),
    reconnect: vi.fn(),
    acceptReconnectRollback: vi.fn(),
    cancelReconnectRollback: vi.fn(),
  }
  render(<MainRibScreen view={view as unknown as WebMainView} />)
  return { view, store, slot }
}

describe('MainRibScreen', () => {
  it('이메일과 세 탭을 그리고 고른 탭만 선택됨으로 알린다', () => {
    setup({ selectedTab: 'generator' }, 'passwordGenerator')

    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: '메인 메뉴' })
    expect(within(nav).getByRole('tab', { name: '생성기' })).toHaveAttribute('aria-selected', 'true')
    expect(within(nav).getByRole('tab', { name: '볼트' })).toHaveAttribute('aria-selected', 'false')
    expect(within(nav).getByRole('tab', { name: '설정' })).toHaveAttribute('aria-selected', 'false')
  })

  it('탭을 누르면 탭 이름을 뷰에 보낸다', () => {
    const { view } = setup()

    fireEvent.click(screen.getByRole('tab', { name: '생성기' }))
    fireEvent.click(screen.getByRole('tab', { name: '설정' }))
    fireEvent.click(screen.getByRole('tab', { name: '볼트' }))

    expect(view.selectTab.mock.calls).toEqual([['generator'], ['settings'], ['vault']])
  })

  it('슬롯의 screenKey에 맞는 화면을 그린다', () => {
    setup({ selectedTab: 'settings' }, 'settings')
    expect(screen.getByText('설정 화면')).toBeInTheDocument()
  })

  it('슬롯이 비어 있으면 탭 내용 없이 틀만 그린다', () => {
    setup({}, null)
    expect(screen.getByRole('navigation', { name: '메인 메뉴' })).toBeInTheDocument()
  })

  it('슬롯이 바뀌면 새 화면으로 바꾼다', () => {
    const { slot } = setup()
    expect(screen.getByText('볼트 목록 화면')).toBeInTheDocument()

    act(() => slot.set({ screenKey: 'passwordGenerator', view: {} }))

    expect(screen.getByText('생성기 화면')).toBeInTheDocument()
    expect(screen.queryByText('볼트 목록 화면')).not.toBeInTheDocument()
  })

  it('잠그기·로그아웃 버튼은 요청을 보낸다', () => {
    const { view } = setup()

    fireEvent.click(screen.getByRole('button', { name: '잠그기' }))
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(view.requestLock).toHaveBeenCalledTimes(1)
    expect(view.requestLogout).toHaveBeenCalledTimes(1)
  })

  describe('안내와 경고', () => {
    it('아무 안내도 없으면 알림을 그리지 않는다', () => {
      setup()
      expect(screen.queryByRole('status')).not.toBeInTheDocument()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })

    it('오프라인이면 상태가 준 안내 문구를 보인다', () => {
      setup({ offline: true, offlineNotice: '오프라인 상태입니다.' })
      expect(screen.getByRole('status')).toHaveTextContent('오프라인 상태입니다.')
    })

    it('읽기 전용이면 상태가 준 안내 문구를 보인다', () => {
      setup({ readOnly: true, readOnlyNotice: '읽기만 할 수 있습니다.' })
      expect(screen.getByRole('status')).toHaveTextContent('읽기만 할 수 있습니다.')
    })

    it('동기화 오류는 제목과 본문을 경고로 보인다', () => {
      setup({ syncError: message('동기화 실패', '서버가 거절했습니다.') })
      const alert = screen.getByRole('alert')
      expect(alert).toHaveTextContent('동기화 실패')
      expect(alert).toHaveTextContent('서버가 거절했습니다.')
    })

    it('기기 저장 경고를 보인다', () => {
      setup({ showsDeviceStorageWarning: true, deviceStorageWarning: '이 기기에 저장하지 못했습니다.' })
      expect(screen.getByRole('alert')).toHaveTextContent('이 기기에 저장하지 못했습니다.')
    })
  })

  describe('로그아웃 확인', () => {
    it('동기화되지 않은 변경 사항을 알리고 확인·취소를 뷰에 보낸다', () => {
      const { view } = setup({ isConfirmingLogout: true })
      const prompt = screen.getByRole('alertdialog', { name: '동기화되지 않은 변경 사항' })

      fireEvent.click(within(prompt).getByRole('button', { name: '취소' }))
      expect(view.cancelLogout).toHaveBeenCalledTimes(1)

      fireEvent.click(within(prompt).getByRole('button', { name: '로그아웃' }))
      expect(view.confirmLogout).toHaveBeenCalledTimes(1)
    })

    it('확인 중이 아니면 보이지 않는다', () => {
      setup()
      expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    })

    it('로그아웃 중에는 확인창 버튼이 진행 중으로 막힌다', () => {
      setup({ isConfirmingLogout: true, isLoggingOut: true })
      expect(within(screen.getByRole('alertdialog')).getByRole('button', { name: '취소' })).toBeDisabled()
    })
  })

  describe('다시 연결', () => {
    it('필요 없으면 비밀번호 칸을 그리지 않는다', () => {
      setup()
      expect(screen.queryByLabelText('마스터 비밀번호')).not.toBeInTheDocument()
    })

    it('필요하면 안내와 비밀번호 칸을 그리고, 입력은 뷰에 보낸다', () => {
      const { view } = setup({ needsReconnect: true, reconnectPassword: 'abc' })

      expect(screen.getByText('서버와 동기화하려면 마스터 비밀번호를 다시 입력해 주세요.')).toBeInTheDocument()
      const field = screen.getByLabelText('마스터 비밀번호')
      expect(field).toHaveValue('abc')

      fireEvent.change(field, { target: { value: 'abcd' } })
      expect(view.updateReconnectPassword).toHaveBeenCalledWith('abcd')
    })

    it('동기화 버튼을 누르면 다시 연결을 요청하고 새로 고침하지 않는다', () => {
      const { view } = setup({ needsReconnect: true, reconnectPassword: 'abc' })

      const form = screen.getByRole('button', { name: '동기화' }).closest('form')!
      expect(fireEvent.submit(form)).toBe(false)

      expect(view.reconnect).toHaveBeenCalledTimes(1)
    })

    it('연결 중에는 비밀번호 칸과 버튼을 막는다', () => {
      setup({ needsReconnect: true, isReconnecting: true })
      expect(screen.getByLabelText('마스터 비밀번호')).toBeDisabled()
      expect(screen.getByRole('button', { name: '동기화' })).toBeDisabled()
    })

    it('연결 오류를 보인다', () => {
      setup({ needsReconnect: true, reconnectError: message('로그인 실패', '비밀번호를 확인해 주세요.') })
      expect(screen.getByRole('alert')).toHaveTextContent('비밀번호를 확인해 주세요.')
    })

    it('서버 볼트가 되돌아갔으면 확인을 받아 받아들이거나 취소한다', () => {
      const { view } = setup({ needsReconnect: true, isConfirmingReconnectRollback: true })
      const prompt = screen.getByRole('alertdialog', { name: '서버 볼트가 이전 상태로 돌아감' })

      fireEvent.click(within(prompt).getByRole('button', { name: '서버 볼트 받아들이기' }))
      expect(view.acceptReconnectRollback).toHaveBeenCalledTimes(1)

      fireEvent.click(within(prompt).getByRole('button', { name: '취소' }))
      expect(view.cancelReconnectRollback).toHaveBeenCalledTimes(1)
    })
  })
})

// 문구는 Kotlin State가 내려 준다. 화면이 직접 들고 있지 않다면 State 값을 바꿨을 때 바뀐 글자가 그려진다
describe('MainRibScreen 정책 문구는 State 값을 그린다', () => {
  it('로그아웃 확인 창의 글자를 State에서 받는다', () => {
    const { view } = setup({
      isConfirmingLogout: true,
      pendingLogoutConfirm: asState<WebConfirmText>({ title: '대기 제목', body: '대기 본문', confirm: '대기 확인', cancel: '대기 취소' }),
    })

    const prompt = screen.getByRole('alertdialog', { name: '대기 제목' })
    expect(prompt).toHaveTextContent('대기 본문')
    fireEvent.click(within(prompt).getByRole('button', { name: '대기 확인' }))
    fireEvent.click(within(prompt).getByRole('button', { name: '대기 취소' }))
    expect(view.confirmLogout).toHaveBeenCalledTimes(1)
    expect(view.cancelLogout).toHaveBeenCalledTimes(1)
  })

  it('다시 연결 안내와 버튼 글자를 State에서 받는다', () => {
    setup({ needsReconnect: true, reconnectPrompt: asState<WebReconnectPrompt>({ body: '다시 연결 안내', action: '다시 연결' }) })

    expect(screen.getByText('다시 연결 안내')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 연결' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '동기화' })).not.toBeInTheDocument()
  })

  it('다시 연결 중 되돌림 확인 창의 글자를 State에서 받는다', () => {
    const { view } = setup({
      needsReconnect: true,
      isConfirmingReconnectRollback: true,
      rollbackWarning: asState<WebConfirmText>({ title: '되돌림 제목', body: '되돌림 본문', confirm: '되돌림 수락', cancel: '되돌림 취소' }),
    })

    const prompt = screen.getByRole('alertdialog', { name: '되돌림 제목' })
    expect(prompt).toHaveTextContent('되돌림 본문')
    fireEvent.click(within(prompt).getByRole('button', { name: '되돌림 수락' }))
    fireEvent.click(within(prompt).getByRole('button', { name: '되돌림 취소' }))
    expect(view.acceptReconnectRollback).toHaveBeenCalledTimes(1)
    expect(view.cancelReconnectRollback).toHaveBeenCalledTimes(1)
  })
})
