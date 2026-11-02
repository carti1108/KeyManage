// toBeInTheDocument 같은 DOM 단언을 Vitest에 추가한다
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Vitest globals를 켜지 않아 Testing Library가 스스로 정리하지 못하므로 테스트마다 그린 화면을 지운다
afterEach(cleanup)

// 전체 실행에서 키 유도(PBKDF2 60만 회)가 여러 작업자에서 겹치면 vi.waitFor 기본 한도(1초)를 넘을 수 있다.
// 한도를 정하지 않은 대기는 10초까지 기다린다. 조건과 어설션은 그대로다
const WAIT_FOR_TIMEOUT_MS = 10_000
const waitFor = vi.waitFor
vi.waitFor = ((callback: Parameters<typeof waitFor>[0], options?: Parameters<typeof waitFor>[1]) =>
  waitFor(callback, options ?? { timeout: WAIT_FOR_TIMEOUT_MS })) as typeof waitFor
