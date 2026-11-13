// vite.config.ts와 vitest.integration.config.ts가 함께 쓰는 별칭. 한 곳에서만 정의해 두 설정이 어긋나지 않게 한다
import { fileURLToPath } from 'node:url'

export const kotlinSessionAliases = [
  // 공유 Kotlin 세션(Apps/Shared/web-session)의 빌드 결과. 먼저 npm run kotlin으로 만든다 (domain-migration-plan 10장)
  {
    find: 'keymanage-web-session',
    replacement: fileURLToPath(
      new URL('../Shared/web-session/build/dist/js/productionLibrary/keymanage-shared-web-session.mjs', import.meta.url),
    ),
  },
  // Ktor JS가 Node 웹소켓용으로만 가져오는 의존성. 쓰지 않으므로 빈 모듈로 바꾼다
  { find: /^ws$/, replacement: fileURLToPath(new URL('./src/adapters/kotlin/wsStub.ts', import.meta.url)) },
]
