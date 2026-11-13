import { act, fireEvent, render, screen } from '@testing-library/react'
import type { WebAuthState, WebAuthView } from 'keymanage-web-session'
import { describe, expect, it, vi } from 'vitest'
import { AuthRibScreen } from './AuthRibScreen'
import { MASTER_PASSWORD_SETUP_TEXT, ROLLBACK_WARNING_TEXT, WEAK_MASTER_PASSWORD_TEXT } from './policyTextFixtures'

const SETUP_TITLE = '마스터 비밀번호 설정'
const SETUP_BODY = '볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다.'
const WEAK_WARNING = '마스터 비밀번호가 보안에 취약합니다. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요.'

const LOGIN_STATE = {
  mode: 'login',
  email: '',
  password: '',
  confirmation: '',
  isLoading: false,
  notice: null,
  error: null,
  isConfirmingRollback: false,
  isRegistering: false,
  showsStrengthGauge: false,
  passwordStrengthScore: 0,
  showsWeakPasswordWarning: false,
  rollbackWarning: ROLLBACK_WARNING_TEXT,
  masterPasswordSetup: MASTER_PASSWORD_SETUP_TEXT,
  weakPasswordWarning: WEAK_MASTER_PASSWORD_TEXT,
}

function renderAuth(overrides: Record<string, unknown> = {}) {
  const state = { ...LOGIN_STATE, ...overrides } as unknown as WebAuthState
  const view = {
    store: { subscribe: () => () => {}, getSnapshot: () => state },
    switchMode: vi.fn(),
    updateEmail: vi.fn(),
    updatePassword: vi.fn(),
    updateConfirmation: vi.fn(),
    submit: vi.fn(),
    acceptRollback: vi.fn(),
    cancelRollback: vi.fn(),
  }
  render(<AuthRibScreen view={view as unknown as WebAuthView} />)
  return view
}

const REGISTER = { mode: 'register', isRegistering: true }

describe('AuthRibScreen 로그인·가입 전환', () => {
  it('로그인 모드는 제목과 로그인 버튼을 그리고 확인 입력란은 없다', () => {
    renderAuth()

    expect(screen.getByRole('heading', { name: 'KeyManage' })).toBeInTheDocument()
    expect(screen.getByText('볼트에 로그인')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByLabelText('마스터 비밀번호 확인')).not.toBeInTheDocument()
  })

  it('가입 모드는 확인 입력란과 가입 버튼을 그린다', () => {
    renderAuth(REGISTER)

    expect(screen.getByText('새 계정 만들기')).toBeInTheDocument()
    expect(screen.getByLabelText('마스터 비밀번호 확인')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가입' })).toBeInTheDocument()
  })

  it('전환 버튼을 누르면 switchMode를 보낸다', () => {
    const view = renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))

    expect(view.switchMode).toHaveBeenCalledTimes(1)
  })

  it('가입 모드의 전환 버튼은 이미 계정이 있어요다', () => {
    renderAuth(REGISTER)

    expect(screen.getByRole('button', { name: '이미 계정이 있어요' })).toBeInTheDocument()
  })

  it('요청 중에는 전환 버튼을 막는다', () => {
    renderAuth({ isLoading: true })

    expect(screen.getByRole('button', { name: '계정 만들기' })).toBeDisabled()
  })
})

describe('AuthRibScreen 입력 전달', () => {
  it('저장소의 값을 입력란에 보이고 입력을 뷰로 보낸다', () => {
    const view = renderAuth({ email: 'a@test.com', password: 'secret' })

    expect(screen.getByLabelText('이메일')).toHaveValue('a@test.com')
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'b@test.com' } })
    fireEvent.change(screen.getByLabelText('마스터 비밀번호'), { target: { value: 'pw' } })

    expect(view.updateEmail).toHaveBeenCalledWith('b@test.com')
    expect(view.updatePassword).toHaveBeenCalledWith('pw')
  })

  it('가입 확인 입력을 뷰로 보낸다', () => {
    const view = renderAuth(REGISTER)
    fireEvent.change(screen.getByLabelText('마스터 비밀번호 확인'), { target: { value: 'again' } })

    expect(view.updateConfirmation).toHaveBeenCalledWith('again')
  })

  it('양식을 제출하면 submit을 보낸다', () => {
    const view = renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(view.submit).toHaveBeenCalledTimes(1)
  })
})

describe('AuthRibScreen 마스터 비밀번호 안내 (SEC-001)', () => {
  it('가입 모드에서 설정 안내를 보이고 입력란 설명으로 잇는다', () => {
    renderAuth(REGISTER)

    expect(screen.getByText(SETUP_TITLE)).toBeInTheDocument()
    expect(screen.getByText(SETUP_BODY)).toBeInTheDocument()
    expect(screen.getByLabelText('마스터 비밀번호')).toHaveAccessibleDescription(`${SETUP_TITLE} ${SETUP_BODY}`)
  })

  it('로그인 모드에서는 안내와 설명 연결이 없다', () => {
    renderAuth()

    expect(screen.queryByText(SETUP_TITLE)).not.toBeInTheDocument()
    expect(screen.getByLabelText('마스터 비밀번호')).not.toHaveAttribute('aria-describedby')
  })
})

describe('AuthRibScreen 가입 강도 게이지 (SEC-002)', () => {
  it('showsStrengthGauge가 false면 게이지와 경고를 보이지 않는다', () => {
    renderAuth(REGISTER)

    expect(screen.queryByRole('meter')).not.toBeInTheDocument()
    expect(screen.queryByText(WEAK_WARNING)).not.toBeInTheDocument()
  })

  it('점수를 게이지에 그린다', () => {
    renderAuth({ ...REGISTER, showsStrengthGauge: true, passwordStrengthScore: 60 })

    expect(screen.getByRole('meter', { name: '비밀번호 강도' })).toHaveAttribute('aria-valuenow', '60')
    expect(screen.queryByText(WEAK_WARNING)).not.toBeInTheDocument()
  })

  it('취약 경고 여부는 상태를 그대로 따른다', () => {
    renderAuth({ ...REGISTER, showsStrengthGauge: true, passwordStrengthScore: 59, showsWeakPasswordWarning: true })

    expect(screen.getByRole('meter', { name: '비밀번호 강도' })).toHaveAttribute('aria-valuenow', '59')
    expect(screen.getByText(WEAK_WARNING)).toBeInTheDocument()
  })

  it('59점에서 60점으로 바뀌면 게이지 값이 따라가고 취약 경고가 걷힌다 (경계)', () => {
    // 점수 판정(60점 미만이 취약)은 Kotlin이 하고, 화면은 상태가 바뀌는 대로 다시 그린다
    let state = { ...LOGIN_STATE, ...REGISTER, showsStrengthGauge: true, passwordStrengthScore: 59, showsWeakPasswordWarning: true } as unknown as WebAuthState
    const listeners = new Set<() => void>()
    const view = {
      store: {
        subscribe: (listener: () => void) => {
          listeners.add(listener)
          return () => listeners.delete(listener)
        },
        getSnapshot: () => state,
      },
    }
    render(<AuthRibScreen view={view as unknown as WebAuthView} />)
    const meter = () => screen.getByRole('meter', { name: '비밀번호 강도' })

    expect(meter()).toHaveAttribute('aria-valuenow', '59')
    expect(screen.getByText(WEAK_WARNING)).toBeInTheDocument()

    state = { ...state, passwordStrengthScore: 60, showsWeakPasswordWarning: false } as unknown as WebAuthState
    act(() => listeners.forEach((listener) => listener()))

    expect(meter()).toHaveAttribute('aria-valuenow', '60')
    expect(screen.queryByText(WEAK_WARNING)).not.toBeInTheDocument()
  })
})

describe('AuthRibScreen 안내와 오류', () => {
  it('로그인 화면으로 돌아온 이유를 안내로 보인다', () => {
    renderAuth({ notice: '로그인이 만료되었습니다. 다시 로그인해 주세요.' })

    expect(screen.getByText('로그인이 만료되었습니다. 다시 로그인해 주세요.')).toBeInTheDocument()
  })

  it('계정을 지운 뒤 돌아온 로그인 화면은 완료 안내를 정책 문구 그대로 보인다 (SEC-005)', () => {
    renderAuth({ notice: '계정을 삭제했습니다.' })

    expect(screen.getByText('계정을 삭제했습니다.')).toBeInTheDocument()
  })

  it('안내가 없으면 그리지 않는다', () => {
    renderAuth()

    expect(screen.queryByText('로그인이 만료되었습니다. 다시 로그인해 주세요.')).not.toBeInTheDocument()
  })

  it('오류의 제목과 본문을 경고로 보인다', () => {
    renderAuth({ error: { title: '로그인 실패', body: '이메일 또는 비밀번호를 확인해 주세요.' } })

    expect(screen.getByRole('alert')).toHaveTextContent('로그인 실패')
    expect(screen.getByRole('alert')).toHaveTextContent('이메일 또는 비밀번호를 확인해 주세요.')
  })

  it('제목 없는 오류는 본문만 보인다', () => {
    renderAuth({ error: { title: null, body: '비밀번호 확인이 일치하지 않습니다.' } })

    expect(screen.getByRole('alert')).toHaveTextContent('비밀번호 확인이 일치하지 않습니다.')
  })

  it('서버 볼트 되돌림 확인에서 받아들이거나 취소한다', () => {
    const view = renderAuth({ isConfirmingRollback: true })

    expect(screen.getByRole('alertdialog')).toHaveTextContent('서버 볼트가 이전 상태로 돌아감')
    fireEvent.click(screen.getByRole('button', { name: '서버 볼트 받아들이기' }))
    fireEvent.click(screen.getByRole('button', { name: '취소' }))

    expect(view.acceptRollback).toHaveBeenCalledTimes(1)
    expect(view.cancelRollback).toHaveBeenCalledTimes(1)
  })

  it('되돌림 확인 중에는 오류를 가리고 확인만 보인다', () => {
    renderAuth({ isConfirmingRollback: true, error: { title: null, body: '오류 본문' } })

    expect(screen.queryByText('오류 본문')).not.toBeInTheDocument()
  })
})

// 문구는 Kotlin State가 내려 준다. 화면이 직접 들고 있지 않다면 State 값을 바꿨을 때 바뀐 글자가 그려진다
describe('AuthRibScreen 정책 문구는 State 값을 그린다', () => {
  it('되돌림 확인 창의 제목·본문·버튼 글자를 State에서 받는다', () => {
    const view = renderAuth({
      isConfirmingRollback: true,
      rollbackWarning: { title: '되돌림 제목', body: '되돌림 본문', confirm: '되돌림 수락', cancel: '되돌림 취소' },
    })

    expect(screen.getByRole('alertdialog')).toHaveTextContent('되돌림 제목')
    expect(screen.getByRole('alertdialog')).toHaveTextContent('되돌림 본문')
    fireEvent.click(screen.getByRole('button', { name: '되돌림 수락' }))
    fireEvent.click(screen.getByRole('button', { name: '되돌림 취소' }))
    expect(view.acceptRollback).toHaveBeenCalledTimes(1)
    expect(view.cancelRollback).toHaveBeenCalledTimes(1)
    expect(screen.queryByText('서버 볼트가 이전 상태로 돌아감')).not.toBeInTheDocument()
  })

  it('가입 안내 제목과 본문을 State에서 받는다', () => {
    renderAuth({ ...REGISTER, masterPasswordSetup: { title: '설정 제목', body: '설정 본문' } })

    expect(screen.getByText('설정 제목')).toBeInTheDocument()
    expect(screen.getByText('설정 본문')).toBeInTheDocument()
    expect(screen.queryByText(SETUP_TITLE)).not.toBeInTheDocument()
  })

  it('취약 경고 문구를 State에서 받는다', () => {
    renderAuth({ ...REGISTER, showsStrengthGauge: true, passwordStrengthScore: 10, showsWeakPasswordWarning: true, weakPasswordWarning: '약함 안내' })

    expect(screen.getByText('약함 안내')).toBeInTheDocument()
    expect(screen.queryByText(WEAK_WARNING)).not.toBeInTheDocument()
  })
})
