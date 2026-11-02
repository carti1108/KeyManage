// @vitest-environment node
// 기기 기록의 옛 KDF 값으로 볼트를 열지 못했을 때, 서버의 KDF 값이 바뀌었으면 비밀번호가 바뀌었다고 안내한다 (SEC-003 규칙 11, SEC-004 규칙 8)
import { describe, expect, it } from 'vitest'
import { PASSWORD, restart, setup } from '../../test/sessionFixtures'
import { MasterPasswordChangedError } from '../auth/authService'
import { WrongMasterPasswordError } from './vaultSession'

const EMAIL = 'user@example.com'
const NEW_PASSWORD = 'a brand new master password'

/** 이 기기에서 로그인해 둔 뒤 같은 서버의 다른 기기가 비밀번호를 바꾼다 */
async function changedElsewhere() {
  const mine = setup()
  await mine.session.login(EMAIL, PASSWORD)
  const other = setup({ authApi: mine.authApi, vaultApi: mine.vaultApi })
  await other.session.login(EMAIL, PASSWORD)
  await other.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)
  return mine
}

describe('다른 곳에서 바뀐 비밀번호로 잠금 해제 (SEC-003 규칙 11)', () => {
  it('세션 없이 기기 기록으로 연 탭에 새 비밀번호를 넣으면 비밀번호가 바뀌었다고 알리고 로그아웃한다', async () => {
    const reopened = await restart(await changedElsewhere())

    await expect(reopened.session.unlock(NEW_PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(reopened.session.state().status).toBe('signedOut')
  })

  it('옛 비밀번호를 넣어도 지금처럼 비밀번호가 바뀌었다고 알린다', async () => {
    const reopened = await restart(await changedElsewhere())

    await expect(reopened.session.unlock(PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(reopened.session.state().status).toBe('signedOut')
  })

  it('토큰이 있는 잠긴 탭에도 새 비밀번호를 넣으면 비밀번호가 바뀌었다고 알리고 로그아웃한다', async () => {
    const mine = await changedElsewhere()
    mine.session.lock()

    await expect(mine.session.unlock(NEW_PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(mine.session.state().status).toBe('signedOut')
  })

  it('서버 KDF 값이 그대로면 틀린 비밀번호는 지금처럼 WrongMasterPasswordError이고 잠긴 채로 둔다', async () => {
    const first = setup()
    await first.session.login(EMAIL, PASSWORD)
    const reopened = await restart(first)

    await expect(reopened.session.unlock('not the password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(reopened.session.state().status).toBe('locked')
  })

  it('서버에 닿지 못하면 확인을 건너뛰고 WrongMasterPasswordError로 둔다', async () => {
    const reopened = await restart(await changedElsewhere())
    reopened.authApi.offline = true

    await expect(reopened.session.unlock(NEW_PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(reopened.session.state().status).toBe('locked')
  })

  it('세션 없이 연 볼트에서 다시 로그인할 때 새 비밀번호를 넣어도 비밀번호가 바뀌었다고 알리고 로그아웃한다', async () => {
    const first = setup()
    await first.session.login(EMAIL, PASSWORD)
    first.authApi.offline = true
    const reopened = await restart(first)
    await reopened.session.unlock(PASSWORD)
    first.authApi.offline = false
    const other = setup({ authApi: first.authApi, vaultApi: first.vaultApi })
    await other.session.login(EMAIL, PASSWORD)
    await other.session.changeMasterPassword(PASSWORD, NEW_PASSWORD)

    await expect(reopened.session.reconnect(NEW_PASSWORD)).rejects.toBeInstanceOf(MasterPasswordChangedError)
    expect(reopened.session.state().status).toBe('signedOut')
  })
})
