import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AccountDeletedDeviceNotClearedError, SessionExpiredError } from '../../domain/session/vaultSession'
import { createFakeClipboard } from '../../test/fakeClipboard'
import { createFakeSession, fakeVault } from '../../test/fakeSession'
import { SessionRoot } from './SessionRoot'

const PASSWORD = 'correct horse battery staple'

function openVault(options: { networkOffline?: boolean } = {}) {
  const fake = createFakeSession()
  fake.control.openVault('me@example.com', fakeVault())
  render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} networkOffline={options.networkOffline} />)
  return fake
}

function deleteAccount() {
  fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))
  const dialog = screen.getByRole('dialog', { name: '계정 삭제' })
  fireEvent.change(within(dialog).getByLabelText('마스터 비밀번호'), { target: { value: PASSWORD } })
  fireEvent.change(within(dialog).getByLabelText("확인을 위해 '계정 삭제'를 입력해 주세요."), { target: { value: '계정 삭제' } })
  fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))
}

describe('SessionRoot 계정 삭제 (SEC-005)', () => {
  it('지우면 로그인 화면으로 가서 완료 안내를 보인다', async () => {
    const { session } = openVault()

    deleteAccount()

    expect(await screen.findByText('계정을 삭제했습니다.')).toBeInTheDocument()
    expect(session.deleteAccount).toHaveBeenCalledWith(PASSWORD)
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('기기 기록을 지우지 못하면 로그인 화면에 직접 지우라는 안내를 보인다', async () => {
    const fake = openVault()
    fake.session.deleteAccount.mockImplementationOnce(async () => {
      fake.control.signOut()
      throw new AccountDeletedDeviceNotClearedError()
    })

    deleteAccount()

    expect(
      await screen.findByText('계정은 삭제했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다. 브라우저의 사이트 데이터를 직접 지워 주세요.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })

  it('브라우저가 오프라인이면 지우지 않고 정책 문구로 알린다', () => {
    const { session } = openVault({ networkOffline: true })

    fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))

    expect(screen.getByText('인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.')).toBeInTheDocument()
    expect(session.deleteAccount).not.toHaveBeenCalled()
  })

  it('세션 없이 연 볼트에서는 오프라인으로 보고 막는다', () => {
    const fake = createFakeSession({ status: 'unlocked', email: 'me@example.com', vault: fakeVault(), pendingSync: false, signedIn: false, storedOnDevice: true })
    render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} />)

    fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))

    expect(screen.getByText('인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.')).toBeInTheDocument()
  })

  it('읽기 전용 볼트에서도 지울 수 있다', async () => {
    const fake = createFakeSession()
    fake.control.openVault('me@example.com', fakeVault({ readOnly: true }))
    render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} />)

    deleteAccount()

    expect(await screen.findByText('계정을 삭제했습니다.')).toBeInTheDocument()
  })

  it('세션이 만료되면 로그인 화면으로 보내며 이유를 알린다', async () => {
    const fake = openVault()
    fake.session.deleteAccount.mockImplementationOnce(async () => {
      fake.control.signOut()
      throw new SessionExpiredError()
    })

    deleteAccount()

    expect(await screen.findByText('로그인이 만료되었습니다. 다시 로그인해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })

  it('다른 기기에서 삭제된 뒤 동기화가 세션 만료를 받으면 로그인 화면으로 간다', async () => {
    const fake = openVault()

    fake.control.signOut()
    fake.control.emitSync(new SessionExpiredError())

    await vi.waitFor(() => expect(screen.getByText('로그인이 만료되었습니다. 다시 로그인해 주세요.')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})
