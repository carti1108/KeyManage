// npm run kotlin이 실행한다: 공유 Kotlin 세션(Apps/Shared/web-session)을 웹이 쓰는 JS 라이브러리로 빌드한다
// Windows cmd는 `cd ../Shared && ./gradlew`를 못 돌리므로, 운영체제에 맞는 Gradle 래퍼(gradlew.bat 또는 gradlew)를 직접 부른다
// 첫 인자가 example이면 피처 Example용 번들(Apps/Shared/web-example)을 빌드한다 (npm run kotlin:example)
// 나머지 인자는 Gradle에 그대로 넘긴다 (예: npm run kotlin -- --console=plain)
import { spawnSync } from 'node:child_process'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const SHARED = fileURLToPath(new URL('../../Shared', import.meta.url))
const isExample = process.argv[2] === 'example'
const TASK = `:${isExample ? 'web-example' : 'web-session'}:jsBrowserProductionLibraryDistribution`
const gradleArgs = process.argv.slice(isExample ? 3 : 2)
const isWindows = process.platform === 'win32'

// .bat은 셸 없이는 실행할 수 없다(Node 보안 수정). 그 밖에는 셸을 거치지 않는다
const wrapper = isWindows ? `"${join(SHARED, 'gradlew.bat')}"` : './gradlew'
const result = spawnSync(wrapper, [TASK, ...gradleArgs], { cwd: SHARED, stdio: 'inherit', shell: isWindows })

if (result.error) {
  console.error(`Gradle 래퍼를 실행하지 못함: ${result.error.message}`)
  process.exit(1)
}
process.exit(result.status ?? 1)
