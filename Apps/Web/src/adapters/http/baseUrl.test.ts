// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { InvalidBaseUrlError, resolveBaseUrl } from './baseUrl'

describe('서버 주소 (ADR-0013, SEC-002)', () => {
  it('비어 있으면 같은 출처로 요청한다', () => {
    expect(resolveBaseUrl(undefined)).toBe('')
    expect(resolveBaseUrl('')).toBe('')
  })

  it('HTTPS 주소와 localhost의 HTTP 주소는 출처만 남겨 쓴다', () => {
    expect(resolveBaseUrl('https://vault.example.com/')).toBe('https://vault.example.com')
    expect(resolveBaseUrl('http://localhost:8080')).toBe('http://localhost:8080')
    expect(resolveBaseUrl('http://127.0.0.1:8080')).toBe('http://127.0.0.1:8080')
  })

  it('거절할 때 오류 메시지에 주소 원문(비밀번호가 섞였을 수 있음)을 넣지 않는다', () => {
    expect(() => resolveBaseUrl('https://user:secret@a.example')).toThrow(expect.objectContaining({ message: expect.not.stringContaining('secret') }))
  })

  it('localhost가 아닌 HTTP, 경로·쿼리가 붙은 주소, 깨진 주소는 거절한다', () => {
    for (const bad of ['http://vault.example.com', 'https://vault.example.com/api', 'https://a.example?x=1', 'https://*.example.com', 'https://user:pw@a.example', 'ftp://a.example', 'not a url']) {
      expect(() => resolveBaseUrl(bad), bad).toThrow(InvalidBaseUrlError)
    }
  })
})
