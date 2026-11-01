# KeyManage Web·데스크톱 클라이언트

브라우저와 데스크톱 앱(Tauri v2)이 함께 쓰는 클라이언트입니다 (ADR-0010, ADR-0012). 서버 렌더링 없는 정적 앱이며, 데스크톱 틀은 화면이 어느 정도 만들어진 뒤 붙입니다.

- 스택: Vite + React + TypeScript, Tailwind CSS(+ shadcn/ui), Vitest, oxlint
- Node 22.12 이상

## 계층 구조

```
src/
├─ domain/     순수 TypeScript: 암호, 볼트 모델·병합, 포트(인터페이스). React와 브라우저 API를 모른다
├─ adapters/   포트 구현: HTTP API, 브라우저 저장소·클립보드 (나중에 Tauri 구현 추가)
├─ ui/         React 화면·컴포넌트. 포트만 알고 adapters는 모른다
└─ app/        조립 지점(Composition Root): 어댑터를 만들어 포트로 화면에 넘긴다
```

의존 방향은 백엔드와 같습니다: `adapters`·`ui` → `domain`, `app` → 전부. `.oxlintrc.json`이 이를 검사합니다.
- `domain`이 React나 `adapters`·`ui`·`app`을 import하거나 브라우저 전역(`window`, `document`, `navigator`, `localStorage`, `sessionStorage`, `fetch`)을 쓰면 lint 실패. 암호는 브라우저·Node·데스크톱 웹뷰에 모두 있는 `globalThis.crypto`를 쓴다
- `ui`가 `adapters`나 `app`을 import하면 lint 실패 (ADR-0004)
- `adapters`가 `ui`나 `app`을 import하면 lint 실패
- 폴더 자체를 가리키는 import(`../adapters`)도 하위 경로(`../adapters/http`)와 똑같이 막는다

## 명령

```bash
npm install
npm run dev      # 개발 서버 (http://localhost:5173). /api는 localhost:8080 백엔드로 프록시
npm run check    # 타입 검사 + lint(경고도 실패) + 테스트
npm run test:integration  # 실제 백엔드에 붙는 통합 테스트 (백엔드를 먼저 띄운다)
npm run build    # 정적 파일 빌드 (dist/)
```

백엔드는 `Back/`에서 `./gradlew :app:bootRun`으로 띄웁니다. 개발 중에는 같은 출처 프록시를 쓰므로 CORS 설정이 필요 없습니다(프록시가 브라우저 `Origin`을 지운다).

통합 테스트(`src/integration/`)는 웹 클라이언트 코드로 가입·두 기기 동기화·충돌 병합·삭제·잠금 흐름을 실제 백엔드에서 확인합니다. 기본 주소는 `http://localhost:8080`이고 `KEYMANAGE_API_URL`로 바꿀 수 있습니다. `npm run check`에는 들어가지 않고, CI의 `integration` 작업이 백엔드를 띄워 실행합니다.

## 데스크톱 앱 (Tauri v2)

`src-tauri/`가 같은 화면을 데스크톱 창으로 감쌉니다. Rust와 플랫폼 빌드 도구(Windows는 Visual Studio C++ 빌드 도구)가 필요합니다.

```bash
npm run tauri dev                          # 개발 서버를 띄우고 데스크톱 창으로 연다
npm run tauri build -- --debug --no-bundle # 디버그 실행 파일만 빌드 (src-tauri/target/debug)
npm run tauri:build                        # 릴리스 빌드(설치 파일 포함). VITE_API_BASE_URL 필요
npm run tauri:build -- --no-bundle         # 릴리스 실행 파일만 빌드 (src-tauri/target/release)
```

릴리스는 반드시 `npm run tauri:build`로 만든다. 이 명령이 서버 주소로 릴리스 CSP(`tauri.release.conf.json`)를 만들어 넘긴다. `npm run tauri build`로 바로 빌드하면 CSP가 서버를 막아 통신할 수 없는 앱이 된다.

- **CSP**: 스크립트·스타일은 앱 자체에서만 불러오고, 네트워크는 앱 자신과 Rust(IPC), 그리고 릴리스 빌드에서는 빌드할 때 정한 서버 출처 하나로만 허용한다(ADR-0013). 악성 코드가 들어와도 복호화된 데이터를 다른 곳으로 보내지 못하게 하기 위해서다. 개발(`tauri dev`)에서는 Vite의 인라인 스타일·스크립트와 HMR 웹소켓을 위해 `devCsp`를 따로 쓴다.
- **권한**: 현재는 `core:default`만 연다. 클립보드 등 플랫폼 기능은 그 기능을 만드는 작업에서 필요한 권한만 추가한다.
- `tauri dev`에서는 창이 Vite 개발 서버를 열므로 `/api` 프록시가 그대로 동작한다. 개발 서버가 이미 떠 있으면 `npm run tauri dev -- --config '{"build":{"beforeDevCommand":""}}'`로 그 서버를 쓴다.
- **빌드된 앱**에는 프록시가 없다. 서버 주소는 빌드할 때 `VITE_API_BASE_URL`로 정하고, HTTPS 출처만 받는다(릴리스는 `http://localhost`도 거절). 서버는 `keymanage.http.allowed-origins`에 앱 출처(Windows는 `http://tauri.localhost`)를 넣어 CORS를 허용해야 한다.

### 릴리스 빌드 로컬 확인

배포 서버 없이 릴리스 앱의 서버 통신을 확인하는 절차다. 2026-11-01에 Windows에서 이 절차로 가입·로그인·항목 저장과 브라우저와의 동기화를 확인했다.

1. 로컬 HTTPS 인증서를 만든다. [mkcert](https://github.com/FiloSottile/mkcert)는 이 PC에만 신뢰되는 루트 인증서를 등록한다(지울 때는 `mkcert -uninstall`).

   ```bash
   mkcert -install
   mkcert -cert-file localhost.pem -key-file localhost-key.pem localhost 127.0.0.1
   ```

2. [Caddy](https://caddyserver.com/)로 `https://localhost:8443`을 백엔드 8080으로 넘긴다. `Caddyfile`:

   ```caddyfile
   {
   	auto_https disable_redirects
   	admin off
   }

   https://localhost:8443 {
   	tls localhost.pem localhost-key.pem
   	reverse_proxy localhost:8080
   }
   ```

   `caddy run --config Caddyfile --adapter caddyfile`로 띄운다.

3. 백엔드를 앱 출처만 허용하도록 띄운다(`Back/`에서): `KEYMANAGE_HTTP_ALLOWED_ORIGINS=http://tauri.localhost ./gradlew :app:bootRun`
4. 서버 주소를 넣어 빌드하고 실행한다: `VITE_API_BASE_URL=https://localhost:8443 npm run tauri:build -- --no-bundle` 뒤 `src-tauri/target/release/keymanage-desktop.exe`
5. 앱에서 가입·로그인·항목 저장을 하고, 브라우저(`npm run dev`)에서 같은 계정으로 항목이 보이는지 확인한다.
