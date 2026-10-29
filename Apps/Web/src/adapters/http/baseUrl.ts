// 빌드할 때 정한 서버 주소 검사. 데스크톱 릴리스 빌드 스크립트(Node)도 쓰므로 다른 모듈을 import하지 않는다

const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1'])
// URL 파서는 호스트에 *도 받아 주므로, CSP 와일드카드가 되지 않게 문자를 제한한다. IPv6는 대괄호 표기
const HOSTNAME = /^(?:[a-z0-9-]+(?:\.[a-z0-9-]+)*|\[[0-9a-f:.]+\])$/

export class InvalidBaseUrlError extends Error {
  constructor(value: string) {
    super(`서버 주소가 올바르지 않음: ${value}`)
    this.name = 'InvalidBaseUrlError'
  }
}

/**
 * 빌드할 때 정한 서버 주소를 출처(scheme://host[:port])로 바꾼다. 비어 있으면 같은 출처('')를 쓴다.
 * 인증 해시와 토큰이 오가므로 localhost가 아니면 HTTPS만 허용한다 (SEC-002)
 * @throws InvalidBaseUrlError
 */
export function resolveBaseUrl(raw: string | undefined): string {
  if (!raw) return ''
  let url: URL
  try {
    url = new URL(raw)
  } catch {
    throw new InvalidBaseUrlError(raw)
  }
  const secure = url.protocol === 'https:' || (url.protocol === 'http:' && LOOPBACK_HOSTS.has(url.hostname))
  // CSP에는 출처 하나만 열기 때문에 경로·쿼리가 붙은 주소는 받지 않는다 (ADR-0013)
  const originOnly = url.pathname === '/' && url.search === '' && url.hash === '' && url.username === '' && url.password === ''
  if (!secure || !originOnly || !HOSTNAME.test(url.hostname)) throw new InvalidBaseUrlError(raw)
  return url.origin
}
