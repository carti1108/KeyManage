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
