// 데스크톱 릴리스 빌드용 Tauri 설정을 만든다: CSP connect-src에 서버 출처 하나만 더한다 (ADR-0013)
// 사용: npm run tauri:build (VITE_API_BASE_URL은 환경 변수나 .env.production에 둔다)
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadEnv } from 'vite'
import { resolveBaseUrl } from '../src/adapters/http/baseUrl.ts'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const BASE_CONFIG = `${ROOT}src-tauri/tauri.conf.json`
export const RELEASE_CONFIG = `${ROOT}src-tauri/tauri.release.conf.json`

/** @throws 서버 주소가 없거나 규칙(HTTPS, 출처만)에 맞지 않거나, 기본 CSP에 connect-src가 없을 때 */
export function releaseCsp(baseCsp: string, apiBaseUrl: string): string {
  const origin = resolveBaseUrl(apiBaseUrl)
  if (origin === '') throw new Error('데스크톱 릴리스 빌드에는 VITE_API_BASE_URL(서버 주소)이 필요함')

  const directives = baseCsp.split(';').map((d) => d.trim())
  const index = directives.findIndex((d) => d.startsWith('connect-src '))
  if (index === -1) throw new Error('기본 CSP에 connect-src가 없음')
  return directives.map((d, i) => (i === index ? `${d} ${origin}` : d)).join('; ')
}

function main() {
  const { VITE_API_BASE_URL = '' } = { ...loadEnv('production', ROOT, 'VITE_'), ...process.env }
  const base = JSON.parse(readFileSync(BASE_CONFIG, 'utf8')) as { app: { security: { csp: string } } }
  const csp = releaseCsp(base.app.security.csp, VITE_API_BASE_URL)
  writeFileSync(RELEASE_CONFIG, `${JSON.stringify({ app: { security: { csp } } }, null, 2)}\n`)
  console.info(`릴리스 CSP connect-src에 ${resolveBaseUrl(VITE_API_BASE_URL)} 추가`)
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main()
