// toBeInTheDocument 같은 DOM 단언을 Vitest에 추가한다
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// Vitest globals를 켜지 않아 Testing Library가 스스로 정리하지 못하므로 테스트마다 그린 화면을 지운다
afterEach(cleanup)
