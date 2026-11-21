// 피처 Example 개발 서버 설정 (npm run example). 배포 빌드(vite.config.ts)와 따로 둔다.
// 화면 코드가 가져오는 'keymanage-web-session'도 Example 번들 폴더의 것으로 돌려, Kotlin 런타임이 한 벌만 뜨게 한다
import { fileURLToPath } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const exampleDist = (file: string) =>
  fileURLToPath(new URL(`../Shared/web-example/build/dist/js/productionLibrary/${file}`, import.meta.url))

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: [
      { find: 'keymanage-web-example', replacement: exampleDist('keymanage-shared-web-example.mjs') },
      { find: 'keymanage-web-session', replacement: exampleDist('keymanage-shared-web-session.mjs') },
      // Ktor JS가 Node 웹소켓용으로만 가져오는 의존성. 쓰지 않으므로 빈 모듈로 바꾼다
      { find: /^ws$/, replacement: fileURLToPath(new URL('./src/adapters/kotlin/wsStub.ts', import.meta.url)) },
    ],
  },
  server: {
    // 앱 개발 서버(5173)와 함께 띄울 수 있게 다른 포트를 쓴다
    port: 5174,
    strictPort: true,
    open: '/example.html',
    watch: {
      ignored: ['**/src-tauri/**'],
    },
  },
})
