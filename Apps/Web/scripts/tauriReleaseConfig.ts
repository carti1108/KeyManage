// 데스크톱 릴리스 CSP: connect-src에 서버 출처 하나만 더한다 (ADR-0013). 실행은 writeTauriReleaseConfig.ts가 한다
import { InvalidBaseUrlError, resolveBaseUrl } from '../src/adapters/http/baseUrl.ts'

/**
 * @returns 릴리스 CSP와 더한 서버 출처
 * @throws 서버 주소가 없거나, HTTPS 출처가 아니거나(릴리스는 localhost도 거절), 기본 CSP에 connect-src가 없을 때
 */
export function releaseCsp(baseCsp: string, apiBaseUrl: string): { csp: string; origin: string } {
  const origin = resolveBaseUrl(apiBaseUrl)
  if (origin === '') throw new Error('데스크톱 릴리스 빌드에는 VITE_API_BASE_URL(서버 주소)이 필요함')
  // 개발용 localhost HTTP 주소로 릴리스하면 같은 기기의 다른 프로그램이 인증 해시를 평문으로 받을 수 있다
  if (!origin.startsWith('https://')) throw new InvalidBaseUrlError()

  const directives = baseCsp.split(';').map((d) => d.trim())
  const index = directives.findIndex((d) => d.startsWith('connect-src '))
  if (index === -1) throw new Error('기본 CSP에 connect-src가 없음')
  return { csp: directives.map((d, i) => (i === index ? `${d} ${origin}` : d)).join('; '), origin }
}
