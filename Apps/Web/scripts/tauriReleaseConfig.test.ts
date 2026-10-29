// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { InvalidBaseUrlError } from '../src/adapters/http/baseUrl.ts'
import { releaseCsp } from './tauriReleaseConfig.ts'

const BASE = "default-src 'self'; connect-src 'self' ipc: http://ipc.localhost; object-src 'none'"

describe('데스크톱 릴리스 CSP (ADR-0013)', () => {
  it('connect-src에 서버 출처 하나만 더한다', () => {
    expect(releaseCsp(BASE, 'https://vault.example.com/')).toEqual({
      csp: "default-src 'self'; connect-src 'self' ipc: http://ipc.localhost https://vault.example.com; object-src 'none'",
      origin: 'https://vault.example.com',
    })
  })

  it('나머지 지시어는 그대로 둔다', () => {
    const { csp } = releaseCsp(BASE, 'https://vault.example.com')

    expect(csp.startsWith("default-src 'self';")).toBe(true)
    expect(csp.endsWith("object-src 'none'")).toBe(true)
  })

  it('서버 주소가 없거나 규칙에 맞지 않으면 빌드를 멈춘다', () => {
    expect(() => releaseCsp(BASE, '')).toThrow(/VITE_API_BASE_URL/)
    for (const bad of ['http://vault.example.com', 'http://localhost:8080', 'http://127.0.0.1:8080', 'https://*.example.com', 'https://vault.example.com/api']) {
      expect(() => releaseCsp(BASE, bad), bad).toThrow(InvalidBaseUrlError)
    }
  })

  it('기본 CSP에 connect-src가 없으면 빌드를 멈춘다', () => {
    expect(() => releaseCsp("default-src 'self'", 'https://vault.example.com')).toThrow(/connect-src/)
  })
})
