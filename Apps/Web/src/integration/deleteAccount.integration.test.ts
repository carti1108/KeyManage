// 웹 클라이언트로 계정을 지우고 실제 백엔드에서 결과를 확인한다 (SEC-005, api-spec 2.5.6)
// 백엔드를 먼저 띄워야 한다: Back에서 ./gradlew :app:bootRun, 그다음 npm run test:integration
import { IDBFactory } from 'fake-indexeddb'
import { beforeAll, describe, expect, it } from 'vitest'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import * as kotlinModule from 'keymanage-web-session'
import { createKotlinApp } from '../app/kotlinApp'
import { SessionExpiredError, WrongMasterPasswordError, type VaultSession } from '../domain/session/sessionTypes'
import { createSingleTabFallback } from '../domain/tabs/inMemoryTabs'
import { activeItems, addItem } from '../domain/vault/vaultData'

const env = (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env
const API = env?.KEYMANAGE_API_URL ?? 'http://localhost:8080'
const PASSWORD = 'integration-delete-master-password'

// 기기마다 저장소와 탭 알림을 따로 둔다. 같은 프로세스의 BroadcastChannel을 나누면 같은 브라우저의 탭이 된다
const device = (): VaultSession => createKotlinApp(kotlinModule, { apiBaseUrl: API, deviceStore: createIndexedDbDeviceStore(new IDBFactory()), tabs: createSingleTabFallback() }).session

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

describe('계정 삭제 통합 (SEC-005)', () => {
  const email = `del-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
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
    await laptop.register(email, PASSWORD)
    await addNamed(laptop, 'GitHub')
    await phone.login(email, PASSWORD)

    expect(names(phone)).toEqual(['GitHub'])
  })

  it('비밀번호가 틀리면 지우지 않는다', async () => {
    await expect(laptop.deleteAccount('not-the-password')).rejects.toBeInstanceOf(WrongMasterPasswordError)
    expect(laptop.state().status).toBe('unlocked')
  })

  it('지우면 이 기기는 로그인 전 상태가 된다', async () => {
    await laptop.deleteAccount(PASSWORD)

    expect(laptop.state().status).toBe('signedOut')
  })

  it('다른 기기는 다음 요청에서 세션 만료를 받는다', async () => {
    await expect(addNamed(phone, 'Late')).rejects.toBeInstanceOf(SessionExpiredError)
    expect(phone.state().status).toBe('signedOut')
  })

  it('지운 계정으로는 로그인할 수 없다', async () => {
    await expect(phone.login(email, PASSWORD)).rejects.toEqual(expect.objectContaining({ code: 'AUTH_FAILED' }))
  })

  it('같은 이메일로 다시 가입하면 빈 볼트로 시작한다', async () => {
    await laptop.register(email, PASSWORD)

    expect(names(laptop)).toEqual([])
  })
})
