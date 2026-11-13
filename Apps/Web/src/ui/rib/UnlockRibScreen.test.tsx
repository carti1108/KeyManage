import { fireEvent, render, screen, within } from '@testing-library/react'
import type { WebUnlockView } from 'keymanage-web-session'
import { describe, expect, it, vi } from 'vitest'
import { PENDING_LOGOUT_TEXT, ROLLBACK_WARNING_TEXT } from './policyTextFixtures'
import { UnlockRibScreen } from './UnlockRibScreen'

const BASE_STATE = {
  email: 'user@test.com',
  password: '',
  isLoading: false,
  error: null,
  isConfirmingRollback: false,
  isConfirmingLogout: false,
  isBiometricsAvailable: false,
  rollbackWarning: ROLLBACK_WARNING_TEXT,
  pendingLogoutConfirm: PENDING_LOGOUT_TEXT,
}

function renderUnlock(overrides: Record<string, unknown> = {}) {
  const state = { ...BASE_STATE, ...overrides }
  const view = {
    store: { subscribe: () => () => {}, getSnapshot: () => state },
    updatePassword: vi.fn(),
    unlock: vi.fn(),
    acceptRollback: vi.fn(),
    cancelRollback: vi.fn(),
    requestLogout: vi.fn(),
    confirmLogout: vi.fn(),
    cancelLogout: vi.fn(),
  }
  render(<UnlockRibScreen view={view as unknown as WebUnlockView} />)
  return view
}

describe('UnlockRibScreen 그리기', () => {
  it('잠김 제목과 이메일, 입력란, 버튼을 그린다', () => {
    renderUnlock()

    expect(screen.getByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
    expect(screen.getByText('user@test.com')).toBeInTheDocument()
    expect(screen.getByLabelText('마스터 비밀번호')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '잠금 해제' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  })

  it('비밀번호 관리자를 위해 숨은 사용자 이름 칸을 둔다', () => {
    renderUnlock()

    const hidden = document.querySelector('input[type="email"][autocomplete="username"]')
    expect(hidden).toHaveValue('user@test.com')
    expect(hidden).toHaveAttribute('hidden')
  })

  it('생체 인증이 가능하다는 값이 와도 웹은 생체 인증 버튼을 그리지 않는다', () => {
    renderUnlock({ isBiometricsAvailable: true })

    expect(screen.queryByRole('button', { name: /생체|Face|Touch/ })).not.toBeInTheDocument()
  })
})

describe('UnlockRibScreen 입력 전달', () => {
  it('저장소의 비밀번호를 보이고 입력을 뷰로 보낸다', () => {
    const view = renderUnlock({ password: 'abc' })

    expect(screen.getByLabelText('마스터 비밀번호')).toHaveValue('abc')
    fireEvent.change(screen.getByLabelText('마스터 비밀번호'), { target: { value: 'abcd' } })

    expect(view.updatePassword).toHaveBeenCalledWith('abcd')
  })

  it('양식을 제출하면 unlock을 보낸다', () => {
    const view = renderUnlock()
    fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))

    expect(view.unlock).toHaveBeenCalledTimes(1)
  })

  it('로그아웃 버튼은 requestLogout을 보낸다', () => {
    const view = renderUnlock()
    fireEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(view.requestLogout).toHaveBeenCalledTimes(1)
  })

  it('요청 중에는 로그아웃 버튼을 막는다', () => {
    renderUnlock({ isLoading: true })

    expect(screen.getByRole('button', { name: '로그아웃' })).toBeDisabled()
  })
})

describe('UnlockRibScreen 오류와 확인', () => {
  it('오류의 제목과 본문을 경고로 보인다', () => {
    renderUnlock({ error: { title: '잠금 해제 실패', body: '마스터 비밀번호가 올바르지 않습니다.' } })

    expect(screen.getByRole('alert')).toHaveTextContent('잠금 해제 실패')
    expect(screen.getByRole('alert')).toHaveTextContent('마스터 비밀번호가 올바르지 않습니다.')
  })

  it('서버 볼트 되돌림 확인에서 받아들이거나 취소한다', () => {
    const view = renderUnlock({ isConfirmingRollback: true })

    expect(screen.getByRole('alertdialog')).toHaveTextContent('서버 볼트가 이전 상태로 돌아감')
    fireEvent.click(screen.getByRole('button', { name: '서버 볼트 받아들이기' }))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(view.acceptRollback).toHaveBeenCalledTimes(1)
    expect(view.cancelRollback).toHaveBeenCalledTimes(1)
  })

  it('동기화되지 않은 변경 사항이 있으면 로그아웃 확인을 묻는다 (VLT-002)', () => {
    const view = renderUnlock({ isConfirmingLogout: true })

    const prompt = screen.getByRole('alertdialog')
    expect(prompt).toHaveTextContent('동기화되지 않은 변경 사항')
    expect(prompt).toHaveTextContent('로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다.')
    fireEvent.click(within(prompt).getByRole('button', { name: '로그아웃' }))
    fireEvent.click(within(prompt).getByRole('button', { name: '취소' }))

    expect(view.confirmLogout).toHaveBeenCalledTimes(1)
    expect(view.cancelLogout).toHaveBeenCalledTimes(1)
  })

  it('확인이 없을 때는 확인 창을 그리지 않는다', () => {
    renderUnlock()

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })
})

// 문구는 Kotlin State가 내려 준다. 화면이 직접 들고 있지 않다면 State 값을 바꿨을 때 바뀐 글자가 그려진다
describe('UnlockRibScreen 정책 문구는 State 값을 그린다', () => {
  it('되돌림 확인 창의 글자를 State에서 받는다', () => {
    const view = renderUnlock({
      isConfirmingRollback: true,
      rollbackWarning: { title: '되돌림 제목', body: '되돌림 본문', confirm: '되돌림 수락', cancel: '되돌림 취소' },
    })

    expect(screen.getByRole('alertdialog')).toHaveTextContent('되돌림 제목')
    expect(screen.getByRole('alertdialog')).toHaveTextContent('되돌림 본문')
    fireEvent.click(screen.getByRole('button', { name: '되돌림 수락' }))
    fireEvent.click(screen.getByRole('button', { name: '되돌림 취소' }))
    expect(view.acceptRollback).toHaveBeenCalledTimes(1)
    expect(view.cancelRollback).toHaveBeenCalledTimes(1)
  })

  it('로그아웃 확인 창의 글자를 State에서 받는다', () => {
    const view = renderUnlock({
      isConfirmingLogout: true,
      pendingLogoutConfirm: { title: '대기 제목', body: '대기 본문', confirm: '대기 확인', cancel: '대기 취소' },
    })

    const prompt = screen.getByRole('alertdialog')
    expect(prompt).toHaveTextContent('대기 제목')
    expect(prompt).toHaveTextContent('대기 본문')
    fireEvent.click(within(prompt).getByRole('button', { name: '대기 확인' }))
    fireEvent.click(within(prompt).getByRole('button', { name: '대기 취소' }))
    expect(view.confirmLogout).toHaveBeenCalledTimes(1)
    expect(view.cancelLogout).toHaveBeenCalledTimes(1)
  })
})
