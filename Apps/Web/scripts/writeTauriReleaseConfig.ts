// npm run tauri:build가 먼저 실행한다: 릴리스용 Tauri 설정(CSP)을 만들어 tauri build --config로 넘긴다 (ADR-0013)
// VITE_API_BASE_URL은 환경 변수나 .env.production에 둔다. 환경 변수가 우선한다 (Vite loadEnv)
import { readFileSync, rmSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { loadEnv } from 'vite'
import { releaseCsp } from './tauriReleaseConfig.ts'

const ROOT = fileURLToPath(new URL('..', import.meta.url))
const BASE_CONFIG = `${ROOT}src-tauri/tauri.conf.json`
const RELEASE_CONFIG = `${ROOT}src-tauri/tauri.release.conf.json`

// 실패했을 때 지난 빌드의 설정(다른 서버 주소)이 남아 그대로 쓰이지 않도록 먼저 지운다
rmSync(RELEASE_CONFIG, { force: true })

const apiBaseUrl = loadEnv('production', ROOT, 'VITE_').VITE_API_BASE_URL ?? ''
const base = JSON.parse(readFileSync(BASE_CONFIG, 'utf8')) as { app: { security: { csp: string } } }
const { csp, origin } = releaseCsp(base.app.security.csp, apiBaseUrl)
writeFileSync(RELEASE_CONFIG, `${JSON.stringify({ app: { security: { csp } } }, null, 2)}\n`)
console.info(`릴리스 CSP connect-src에 ${origin} 추가`)
