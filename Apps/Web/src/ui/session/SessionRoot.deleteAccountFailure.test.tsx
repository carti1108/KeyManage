import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { NetworkError, ServerError } from '../../domain/server/serverErrors'
import { WrongMasterPasswordError } from '../../domain/session/vaultSession'
import { VaultSyncConflictError } from '../../domain/vault/vaultSync'
import { createFakeClipboard } from '../../test/fakeClipboard'
import { createFakeSession } from '../../test/fakeSession'
import { SessionRoot } from './SessionRoot'

const EMAIL = 'me@example.com'
const PASSWORD = 'correct horse battery staple'
const OFFLINE = '오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.'
const CONFLICT = '다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요.'

/** 서버에 닿지 못해 기기에 저장한 볼트로 열고, 동기화 경고가 떠 있는 볼트 화면 */
async function vaultWithWarnings() {
  const fake = createFakeSession({ status: 'locked', email: EMAIL, pendingSync: false })
  fake.session.unlock.mockImplementationOnce(async () => {
    fake.control.openVault(EMAIL)
    return { offline: true }
  })
  render(<SessionRoot session={fake.session} clipboard={createFakeClipboard().clipboard} pageHidden={false} />)
  fireEvent.change(screen.getByLabelText('마스터 비밀번호'), { target: { value: PASSWORD } })
  fireEvent.click(screen.getByRole('button', { name: '잠금 해제' }))
  expect(await screen.findByText(OFFLINE)).toBeInTheDocument()
  fake.control.emitSync(new VaultSyncConflictError())
  expect(await screen.findByText(CONFLICT)).toBeInTheDocument()
  return fake
}

describe('SessionRoot 계정 삭제 실패 (SEC-005)', () => {
  it.each([
    [new WrongMasterPasswordError(), '마스터 비밀번호가 올바르지 않습니다.'],
    [new ServerError(429, 'TOO_MANY_ATTEMPTS', 900), '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.'],
    [new NetworkError(), '서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.'],
  ])('삭제가 실패하면 기존 동기화 경고와 오프라인 표시를 그대로 둔다 (%s)', async (error, message) => {
    const fake = await vaultWithWarnings()
    fake.session.deleteAccount.mockRejectedValueOnce(error)

    fireEvent.click(screen.getByRole('button', { name: '계정 삭제' }))
    const dialog = screen.getByRole('dialog', { name: '계정 삭제' })
    fireEvent.change(within(dialog).getByLabelText('마스터 비밀번호'), { target: { value: PASSWORD } })
    fireEvent.change(within(dialog).getByLabelText("확인을 위해 '계정 삭제'를 입력해 주세요."), { target: { value: '계정 삭제' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

    expect(await within(dialog).findByText(message)).toBeInTheDocument()
    expect(screen.getByText(OFFLINE)).toBeInTheDocument()
    expect(screen.getByText(CONFLICT)).toBeInTheDocument()
  })
})
