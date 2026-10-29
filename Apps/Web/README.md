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
npm run build    # 정적 파일 빌드 (dist/)
```

백엔드는 `Back/`에서 `./gradlew :app:bootRun`으로 띄웁니다. 개발 중에는 같은 출처 프록시를 쓰므로 CORS 설정이 필요 없습니다.
