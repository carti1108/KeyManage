// 웹 클라이언트로 마스터 비밀번호를 바꾸고 실제 백엔드에서 결과를 확인한다 (SEC-004, api-spec 2.5.5)
// 백엔드를 먼저 띄워야 한다: Back에서 ./gradlew :app:bootRun, 그다음 npm run test:integration
import { IDBFactory } from 'fake-indexeddb'
import { beforeAll, describe, expect, it } from 'vitest'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import { createServices } from '../app/services'
import { SessionExpiredError, WrongMasterPasswordError, type VaultSession } from '../domain/session/vaultSession'
import { activeItems, addItem } from '../domain/vault/vaultData'

const env = (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env
const API = env?.KEYMANAGE_API_URL ?? 'http://localhost:8080'
const OLD_PASSWORD = 'integration-old-master-password'
const NEW_PASSWORD = 'integration-new-master-password'

const device = (): VaultSession => createServices(API, createIndexedDbDeviceStore(new IDBFactory())).session

function names(session: VaultSession) {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error(`볼트가 열려 있지 않음: ${state.status}`)
  return activeItems(state.vault.data)
    .map((item) => item.serviceName)
    .sort()
}

async function addNamed(session: VaultSession, serviceName: string) {
  const state = session.state()
  if (state.status !== 'unlocked') throw new Error('볼트가 열려 있지 않음')
  await session.save(addItem(state.vault.data, { serviceName, username: 'me', password: `${serviceName}-pw` }, new Date(), globalThis.crypto.randomUUID()))
}

describe('마스터 비밀번호 변경 통합 (SEC-004)', () => {
  const email = `pw-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
  const laptop = device()
  const phone = device()

  beforeAll(async () => {
    const response = await fetch(`${API}/api/v1/auth/prelogin`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'probe@example.com' }),
    }).catch(() => null)
    if (response?.status === 429) throw new Error('인증 API 시도 제한(IP당 1분 30회)에 걸림. 1분 뒤 다시 돌린다')
    if (!response?.ok) throw new Error(`백엔드(${API})에 연결할 수 없음. Back에서 ./gradlew :app:bootRun으로 먼저 띄운다`)
  })

  it('가입하고 두 기기에서 같은 볼트를 연다', async () => {
    await laptop.register(email, OLD_PASSWORD)
    await addNamed(laptop, 'GitHub')
    await phone.login(email, OLD_PASSWORD)

    expect(names(phone)).toEqual(['GitHub'])
  })

  it('현재 비밀번호가 틀리면 바꾸지 않는다', async () => {
    await expect(laptop.changeMasterPassword('not-the-password', NEW_PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
  })

  it('비밀번호를 바꾸면 볼트는 그대로이고 바꾼 기기는 새 세션으로 계속 저장한다', async () => {
    await laptop.changeMasterPassword(OLD_PASSWORD, NEW_PASSWORD)
    await addNamed(laptop, 'Mail')

    expect(laptop.state()).toEqual(expect.objectContaining({ status: 'unlocked', pendingSync: false, signedIn: true }))
    laptop.lock()
    await expect(laptop.unlock(OLD_PASSWORD)).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(await laptop.unlock(NEW_PASSWORD)).toEqual({ offline: false })
    expect(names(laptop)).toEqual(['GitHub', 'Mail'])
  })

  it('다른 기기는 다음 요청에서 세션 만료를 받는다', async () => {
    phone.lock()
    await expect(phone.unlock(OLD_PASSWORD)).rejects.toBeInstanceOf(SessionExpiredError)
    expect(phone.state().status).toBe('signedOut')
  })

  it('옛 비밀번호로는 로그인할 수 없고 새 비밀번호로 로그인하면 바뀐 볼트를 받는다', async () => {
    await expect(phone.login(email, OLD_PASSWORD)).rejects.toEqual(expect.objectContaining({ code: 'AUTH_FAILED' }))
    await phone.login(email, NEW_PASSWORD)

    expect(names(phone)).toEqual(['GitHub', 'Mail'])
  })
})
