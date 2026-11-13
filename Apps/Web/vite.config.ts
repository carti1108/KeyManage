import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'
import { kotlinSessionAliases } from './vite.alias.ts'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: kotlinSessionAliases,
  },
  server: {
    // 데스크톱 틀(tauri.conf.json devUrl)이 이 포트를 열므로, 사용 중이면 다른 포트로 넘어가지 않고 실패한다
    port: 5173,
    strictPort: true,
    // Rust 빌드 결과(src-tauri/target)를 감시하면 잠긴 파일 때문에 오류가 나므로 데스크톱 틀 폴더는 감시하지 않는다
    watch: {
      ignored: ['**/src-tauri/**'],
    },
    // 개발 중에는 같은 출처로 백엔드에 붙어 CORS 설정 없이 쓴다. 배포 방식은 아직 정하지 않았다 (open-questions)
    // 프록시는 브라우저가 붙인 Origin(localhost:5173)을 그대로 넘겨 백엔드 CORS가 403 ORIGIN_NOT_ALLOWED로 막는다.
    // 같은 출처 요청처럼 보이도록 Origin을 지운다. 개발 서버에만 해당하고 배포 빌드의 CORS는 그대로다
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        configure: (proxy) => proxy.on('proxyReq', (request) => request.removeHeader('origin')),
      },
    },
  },
  test: {
    environment: 'jsdom',
    // 실제 백엔드가 필요한 통합 테스트는 npm run test:integration으로 따로 돌린다
    exclude: [...configDefaults.exclude, '**/*.integration.test.ts'],
    setupFiles: ['./src/test/setup.ts'],
  },
})
