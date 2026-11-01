// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { DEVICE_RECORD_VERSION, parseDeviceRecord, type DeviceRecord } from './deviceRecord'

const record: DeviceRecord = {
  version: DEVICE_RECORD_VERSION,
  email: 'user@example.com',
  kdf: { salt: 'AAAAAAAAAAAAAAAAAAAAAA==', iterations: 600_000 },
  payload: { version: 1, cipher: 'AES-256-GCM', iv: 'aXY=', ciphertext: 'Y3Q=', tag: 'dGFn' },
  revision: 3,
  lastSeenRevision: 4,
  pendingSync: true,
}

describe('기기 기록 읽기 (ADR-0019)', () => {
  it('규격에 맞는 기록을 그대로 돌려준다', () => {
    expect(parseDeviceRecord(structuredClone(record))).toEqual(record)
  })

  it('저장된 기록이 없으면 null', () => {
    expect(parseDeviceRecord(undefined)).toBeNull()
    expect(parseDeviceRecord(null)).toBeNull()
  })

  it('형식 버전이 다르면(새 버전 앱이 쓴 기록 등) 쓰지 않는다', () => {
    expect(parseDeviceRecord({ ...record, version: 2 })).toBeNull()
  })

  it('필드가 빠졌거나 타입이 틀린 기록은 쓰지 않는다', () => {
    const broken: unknown[] = [
      'text',
      { ...record, email: 42 },
      { ...record, kdf: { salt: 'AAAA' } },
      { ...record, kdf: { salt: 'AAAA', iterations: 1.5 } },
      { ...record, kdf: { salt: record.kdf.salt, iterations: 5 } },
      { ...record, kdf: { salt: record.kdf.salt, iterations: 20_000_000 } },
      { ...record, kdf: { salt: 'AAAA', iterations: 600_000 } },
      { ...record, kdf: { salt: '!!not base64!!', iterations: 600_000 } },
      { ...record, payload: { ...record.payload, iv: 7 } },
      { ...record, payload: null },
      { ...record, revision: -1 },
      { ...record, lastSeenRevision: '4' },
      { ...record, pendingSync: 'yes' },
    ]
    for (const value of broken) expect(parseDeviceRecord(value)).toBeNull()
  })
})
