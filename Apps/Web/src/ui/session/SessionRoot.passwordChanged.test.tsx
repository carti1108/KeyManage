import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MasterPasswordChangedError } from '../../domain/auth/authService'
import { createFakeClipboard } from '../../test/fakeClipboard'
import { createFakeSession, fakeVault } from '../../test/fakeSession'
import { SessionRoot } from './SessionRoot'

const PASSWORD_CHANGED = '마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요.'

describe('SessionRoot 다른 탭의 비밀번호 변경 감지 (SEC-003 규칙 11)', () => {
  it('열린 탭이 비밀번호 바뀜을 알게 되면 로그인 화면에 안내하고, 로그인한 뒤 볼트 화면에 그 경고를 남기지 않는다', async () => {
    const fake = createFakeSession()
    fake.control.openVault('me@example.com', fakeVault())
    render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} />)

    // 실제 세션처럼 상태를 지운 뒤 알린다
    fake.control.signOut()
    fake.control.emitSync(new MasterPasswordChangedError())

    expect(await screen.findByText(PASSWORD_CHANGED)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'me@example.com' } })
    fireEvent.change(screen.getByLabelText('마스터 비밀번호'), { target: { value: 'a brand new master password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('button', { name: '잠그기' })).toBeInTheDocument()
    expect(screen.queryByText(PASSWORD_CHANGED)).toBeNull()
  })

  it('잠긴 탭이 비밀번호 바뀜을 알게 되어도 로그인 화면에 안내한다', async () => {
    const fake = createFakeSession({ status: 'locked', email: 'me@example.com', pendingSync: false })
    render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} />)

    fake.control.signOut()
    fake.control.emitSync(new MasterPasswordChangedError())

    expect(await screen.findByText(PASSWORD_CHANGED)).toBeInTheDocument()
  })
})
