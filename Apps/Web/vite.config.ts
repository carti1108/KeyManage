/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // 데스크톱 틀(tauri.conf.json devUrl)이 이 포트를 열므로, 사용 중이면 다른 포트로 넘어가지 않고 실패한다
    port: 5173,
    strictPort: true,
    // Rust 빌드 결과(src-tauri/target)를 감시하면 잠긴 파일 때문에 오류가 나므로 데스크톱 틀 폴더는 감시하지 않는다
    watch: {
      ignored: ['**/src-tauri/**'],
    },
    // 개발 중에는 같은 출처로 백엔드에 붙어 CORS 설정 없이 쓴다. 배포 방식은 아직 정하지 않았다 (open-questions)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
