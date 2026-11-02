import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SessionExpiredError } from '../../domain/session/vaultSession'
import { createFakeClipboard } from '../../test/fakeClipboard'
import { createFakeSession, fakeVault } from '../../test/fakeSession'
import { SessionRoot } from './SessionRoot'

const CURRENT = 'correct horse battery staple'
const NEXT = 'a brand new master password'

function openVault(options: { networkOffline?: boolean } = {}) {
  const fake = createFakeSession()
  fake.control.openVault('me@example.com', fakeVault())
  render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} networkOffline={options.networkOffline} />)
  return fake
}

function changePassword() {
  fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))
  const dialog = screen.getByRole('dialog', { name: '마스터 비밀번호 변경' })
  fireEvent.change(within(dialog).getByLabelText('현재 마스터 비밀번호'), { target: { value: CURRENT } })
  fireEvent.change(within(dialog).getByLabelText('새 마스터 비밀번호'), { target: { value: NEXT } })
  fireEvent.change(within(dialog).getByLabelText('새 마스터 비밀번호 확인'), { target: { value: NEXT } })
  fireEvent.click(within(dialog).getByRole('button', { name: '변경' }))
}

describe('SessionRoot 마스터 비밀번호 변경 (SEC-004)', () => {
  it('열린 볼트 화면에서 비밀번호를 바꾸고 완료 문구를 보인다', async () => {
    const { session } = openVault()

    changePassword()

    await vi.waitFor(() => expect(screen.getByText('마스터 비밀번호를 바꿨습니다.')).toBeInTheDocument())
    expect(session.changeMasterPassword).toHaveBeenCalledWith(CURRENT, NEXT)
  })

  it('브라우저가 오프라인이면 바꾸지 않고 정책 문구로 알린다', () => {
    const { session } = openVault({ networkOffline: true })

    fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))

    expect(screen.getByText('인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.')).toBeInTheDocument()
    expect(session.changeMasterPassword).not.toHaveBeenCalled()
  })

  it('세션 없이 연 볼트에서는 오프라인으로 보고 막는다', () => {
    const fake = createFakeSession({ status: 'unlocked', email: 'me@example.com', vault: fakeVault(), pendingSync: false, signedIn: false, storedOnDevice: true })
    render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} />)

    fireEvent.click(screen.getByRole('button', { name: '마스터 비밀번호 변경' }))

    expect(screen.getByText('인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.')).toBeInTheDocument()
  })

  it('세션이 만료되면 로그인 화면으로 보내며 이유를 알린다', async () => {
    const fake = openVault()
    fake.session.changeMasterPassword.mockImplementationOnce(async () => {
      fake.control.signOut()
      throw new SessionExpiredError()
    })

    changePassword()

    expect(await screen.findByText('로그인이 만료되었습니다. 다시 로그인해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})
