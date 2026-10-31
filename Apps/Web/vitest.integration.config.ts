// 실제 백엔드에 붙는 통합 테스트 설정. 백엔드를 먼저 띄운 뒤 npm run test:integration으로 돌린다
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    include: ['src/**/*.integration.test.ts'],
    environment: 'node',
    // 키 유도(PBKDF2 60만 회)와 네트워크 왕복이 겹쳐 단위 테스트보다 오래 걸린다
    testTimeout: 60_000,
    // 한 계정으로 이어지는 흐름이라 순서대로 돌린다
    sequence: { concurrent: false },
  },
})
