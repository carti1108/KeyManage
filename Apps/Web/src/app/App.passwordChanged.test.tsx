import { render, screen } from '@testing-library/react'
import { IDBFactory } from 'fake-indexeddb'
import { describe, expect, it } from 'vitest'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import { DEVICE_RECORD_VERSION } from '../domain/device/deviceRecord'
import type { RememberedKdf, TabMemory } from '../domain/device/tabMemory'
import { createSingleTabFallback } from '../domain/tabs/inMemoryTabs'
import App from './App'
import * as kotlinModule from 'keymanage-web-session'
import { bootKotlinApp, createKotlinApp, type KotlinAppDeps } from './kotlinApp'

const API = 'http://localhost:8080'

async function renderApp(deps: KotlinAppDeps) {
  const kotlinApp = createKotlinApp(kotlinModule, { apiBaseUrl: API, ...deps })
  await bootKotlinApp(kotlinApp)
  render(<App kotlinApp={kotlinApp} />)
}

function memoryOf(value: RememberedKdf | null): TabMemory {
  let current = value
  return {
    get: () => current,
    set: (next) => {
      current = next
    },
    clear: () => {
      current = null
    },
  }
}

describe('App 새로 열린 탭의 비밀번호 바뀜 감지 (SEC-003 규칙 11)', () => {
  it('이 탭이 기억한 KDF 값과 기기 기록이 다르면 로그인 화면에 비밀번호 바뀜을 안내한다', async () => {
    const factory = new IDBFactory()
    await createIndexedDbDeviceStore(factory).save({
      version: DEVICE_RECORD_VERSION,
      email: 'user@example.com',
      kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
      payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' },
      revision: 2,
      lastSeenRevision: 2,
      pendingSync: false,
    })
    const remembered = memoryOf({ email: 'user@example.com', salt: 'BBBBBBBBBBBBBBBBBBBBBB==', iterations: 600_000 })

    await renderApp({ deviceStore: createIndexedDbDeviceStore(factory), tabs: createSingleTabFallback(), tabMemory: remembered })

    expect(await screen.findByText('마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})
