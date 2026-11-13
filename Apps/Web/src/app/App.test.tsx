import { act, render, screen } from '@testing-library/react'
import { IDBFactory } from 'fake-indexeddb'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import { DEVICE_RECORD_VERSION } from '../domain/device/deviceRecord'
import App from './App'
import * as kotlinModule from 'keymanage-web-session'
import { bootKotlinApp, createKotlinApp, type KotlinAppDeps } from './kotlinApp'

const API = 'http://localhost:8080'

async function renderApp(deps: KotlinAppDeps) {
  const kotlinApp = createKotlinApp(kotlinModule, { apiBaseUrl: API, ...deps })
  await bootKotlinApp(kotlinApp)
  render(<App kotlinApp={kotlinApp} />)
}

describe('App', () => {
  afterEach(() => vi.restoreAllMocks())

  it('기기 기록이 없으면 로그인 화면을 보여 준다', async () => {
    await renderApp({ deviceStore: createIndexedDbDeviceStore(new IDBFactory()) })

    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'KeyManage' })).toBeInTheDocument()
  })

  it('기기 기록이 있으면 새로고침 뒤 그 계정의 잠금 화면으로 시작한다 (SEC-003, ADR-0019)', async () => {
    const factory = new IDBFactory()
    await createIndexedDbDeviceStore(factory).save({
      version: DEVICE_RECORD_VERSION,
      email: 'user@example.com',
      kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
      payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' },
      revision: 1,
      lastSeenRevision: 1,
      pendingSync: false,
    })

    await renderApp({ deviceStore: createIndexedDbDeviceStore(factory) })

    expect(await screen.findByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
  })

  it('잠긴 동안 브라우저 연결이 끊기고 돌아와도 잠금 화면을 그대로 둔다 (VLT-002)', async () => {
    const factory = new IDBFactory()
    await createIndexedDbDeviceStore(factory).save({
      version: DEVICE_RECORD_VERSION,
      email: 'user@example.com',
      kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
      payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' },
      revision: 1,
      lastSeenRevision: 1,
      pendingSync: true,
    })
    await renderApp({ deviceStore: createIndexedDbDeviceStore(factory) })
    expect(await screen.findByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()

    const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    act(() => window.dispatchEvent(new Event('offline')))
    expect(screen.getByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()

    onLine.mockReturnValue(true)
    act(() => window.dispatchEvent(new Event('online')))

    expect(screen.getByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
    expect(screen.queryByText('오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다.')).not.toBeInTheDocument()
  })

  describe('탭 가림 (SEC-003, pageHidden)', () => {
    // 탭이 백그라운드로 가거나 돌아온 것을 브라우저처럼 알린다
    function setPageHidden(hidden: boolean) {
      vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(hidden ? 'hidden' : 'visible')
      act(() => document.dispatchEvent(new Event('visibilitychange')))
    }

    it('로그인 화면도 탭이 가려지면 가림 화면으로 덮고 돌아오면 걷는다', async () => {
      await renderApp({ deviceStore: createIndexedDbDeviceStore(new IDBFactory()) })
      expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
      expect(screen.queryByTestId('privacy-cover')).not.toBeInTheDocument()

      setPageHidden(true)

      expect(screen.getByTestId('privacy-cover')).toBeInTheDocument()
      // 가려진 동안에는 보조 기술과 키보드도 아래 화면에 닿지 않는다
      expect(screen.queryByRole('button', { name: '로그인' })).not.toBeInTheDocument()

      setPageHidden(false)

      expect(screen.queryByTestId('privacy-cover')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
    })

    it('잠금 화면도 탭이 가려지면 덮어 평문으로 펼친 비밀번호가 비치지 않게 한다', async () => {
      const factory = new IDBFactory()
      await createIndexedDbDeviceStore(factory).save({
        version: DEVICE_RECORD_VERSION,
        email: 'user@example.com',
        kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
        payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' },
        revision: 1,
        lastSeenRevision: 1,
        pendingSync: false,
      })
      await renderApp({ deviceStore: createIndexedDbDeviceStore(factory) })
      expect(await screen.findByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()

      setPageHidden(true)

      expect(screen.getByTestId('privacy-cover')).toBeInTheDocument()
      expect(screen.queryByRole('heading', { name: '볼트가 잠겼습니다' })).not.toBeInTheDocument()

      setPageHidden(false)

      expect(screen.queryByTestId('privacy-cover')).not.toBeInTheDocument()
      expect(screen.getByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
    })
  })

  it('기기 저장소를 읽지 못해도 로그인 화면으로 시작한다', async () => {
    const broken = { open: () => { throw new Error('사생활 보호 모드') } } as unknown as globalThis.IDBFactory

    await renderApp({ deviceStore: createIndexedDbDeviceStore(broken) })

    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})
