import { render, screen } from '@testing-library/react'
import { IDBFactory } from 'fake-indexeddb'
import { describe, expect, it } from 'vitest'
import { createIndexedDbDeviceStore } from '../adapters/browser/indexedDbDeviceStore'
import { DEVICE_RECORD_VERSION } from '../domain/device/deviceRecord'
import App from './App'
import { createServices } from './services'

const API = 'http://localhost:8080'

describe('App', () => {
  it('기기 기록이 없으면 로그인 화면을 보여 준다', async () => {
    render(<App services={createServices(API, createIndexedDbDeviceStore(new IDBFactory()))} />)

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

    render(<App services={createServices(API, createIndexedDbDeviceStore(factory))} />)

    expect(await screen.findByRole('heading', { name: '볼트가 잠겼습니다' })).toBeInTheDocument()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
  })

  it('기기 저장소를 읽지 못해도 로그인 화면으로 시작한다', async () => {
    const broken = { open: () => { throw new Error('사생활 보호 모드') } } as unknown as globalThis.IDBFactory

    render(<App services={createServices(API, createIndexedDbDeviceStore(broken))} />)

    expect(await screen.findByRole('button', { name: '로그인' })).toBeInTheDocument()
  })
})
