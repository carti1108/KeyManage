import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AuthScreen } from './AuthScreen'

const SETUP_TITLE = '마스터 비밀번호 설정'
const SETUP_BODY = '볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다.'

function renderAuth() {
  render(<AuthScreen onLogin={vi.fn(async () => {})} onRegister={vi.fn(async () => {})} />)
}

describe('AuthScreen 마스터 비밀번호 안내 (SEC-001)', () => {
  it('가입 모드에서 마스터 비밀번호 설정 안내를 보이고 입력란 설명으로 잇는다', () => {
    renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))

    expect(screen.getByText(SETUP_TITLE)).toBeInTheDocument()
    expect(screen.getByText(SETUP_BODY)).toBeInTheDocument()
    expect(screen.getByLabelText('마스터 비밀번호')).toHaveAccessibleDescription(`${SETUP_TITLE} ${SETUP_BODY}`)
  })

  it('로그인 모드에서는 안내를 보이지 않는다', () => {
    renderAuth()

    expect(screen.queryByText(SETUP_TITLE)).not.toBeInTheDocument()
    expect(screen.queryByText(SETUP_BODY)).not.toBeInTheDocument()
    expect(screen.getByLabelText('마스터 비밀번호')).not.toHaveAttribute('aria-describedby')
  })

  it('가입에서 로그인으로 돌아가면 안내를 내린다', () => {
    renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))
    fireEvent.click(screen.getByRole('button', { name: '이미 계정이 있어요' }))

    expect(screen.queryByText(SETUP_BODY)).not.toBeInTheDocument()
  })
})

const WEAK_WARNING = '마스터 비밀번호가 보안에 취약합니다. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요.'
const SCORE_59 = 'Abcdefgh12'
const SCORE_60 = 'abcdefghijk한'

function typePassword(value: string) {
  fireEvent.change(screen.getByLabelText('마스터 비밀번호'), { target: { value } })
}

describe('AuthScreen 가입 강도 게이지 (SEC-002)', () => {
  it('가입 모드에서 비밀번호가 비어 있으면 게이지를 보이지 않는다', () => {
    renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))

    expect(screen.queryByRole('meter')).not.toBeInTheDocument()
  })

  it('로그인 모드에서는 비밀번호를 입력해도 게이지를 보이지 않는다', () => {
    renderAuth()
    typePassword(SCORE_59)

    expect(screen.queryByRole('meter')).not.toBeInTheDocument()
    expect(screen.queryByText(WEAK_WARNING)).not.toBeInTheDocument()
  })

  it('59점이면 게이지와 취약 경고를 보인다', () => {
    renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))
    typePassword(SCORE_59)

    expect(screen.getByRole('meter', { name: '비밀번호 강도' })).toHaveAttribute('aria-valuenow', '59')
    expect(screen.getByText(WEAK_WARNING)).toBeInTheDocument()
  })

  it('60점이면 게이지만 보이고 경고는 없다', () => {
    renderAuth()
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))
    typePassword(SCORE_60)

    expect(screen.getByRole('meter', { name: '비밀번호 강도' })).toHaveAttribute('aria-valuenow', '60')
    expect(screen.queryByText(WEAK_WARNING)).not.toBeInTheDocument()
  })

  it('취약해도 가입은 막지 않는다', async () => {
    const onRegister = vi.fn(async () => {})
    render(<AuthScreen onLogin={vi.fn(async () => {})} onRegister={onRegister} />)
    fireEvent.click(screen.getByRole('button', { name: '계정 만들기' }))
    const weak = 'aaaaaaaaaaaa'
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'user@test.com' } })
    typePassword(weak)
    fireEvent.change(screen.getByLabelText('마스터 비밀번호 확인'), { target: { value: weak } })

    expect(screen.getByText(WEAK_WARNING)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '가입' }))

    await vi.waitFor(() => expect(onRegister).toHaveBeenCalledWith('user@test.com', weak))
  })
})
