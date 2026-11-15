# 2026-11-15 · Figma DS Builder를 tokens.json과 연결

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** Figma 플러그인 `Tools/figma-builder`가 예전 값(slate·sky 팔레트, 다크 단일 모드, 옛 글자 스타일)을 하드코딩하고 있었다. 생성기가 `code.js`의 표시 블록에 `tokens.json` 값을 넣도록 하고, 플러그인이 코드와 같은 이름의 변수(`color/*`, `space/*`, `radius/*`, Light·Dark 모드)와 글자 역할 텍스트 스타일을 만들게 했다. Figma MCP 호출 없이 무료 플랜에서 쓰는 방식이다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)

## 요청

Figma MCP 유료 한도를 쓰지 않고, 기존처럼 DS Builder 플러그인으로 Figma에 그리는 방식을 유지하면서 `tokens.json`과 맞춰 달라고 했다.

## AI 산출물

- `generate.mjs`: 세 번째 출력으로 `code.js`의 `@generated:tokens:start`~`end` 블록을 다시 쓴다. `--check`에도 포함된다.
- `code.js`: 기본 팔레트·시멘틱 별칭·옛 글자 스타일 삭제. `KeyManage` 컬렉션(Light·Dark), `color/*`·`space/*`·`radius/*` 변수, `typography/<역할>`(+`-mono`) 텍스트 스타일 생성. `rgba()` 색 지원. 모드 추가가 막히면(무료 플랜) Light만 등록하고 경고.
- `.githooks/pre-commit` 토큰 검사 대상에 `code.js` 추가, 두 README 갱신

## 사람의 판단

- MCP 대신 플러그인 방식을 유지하기로 했다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `node Tools/design-tokens/generate.mjs --check` | 통과 |
| `node --check Tools/figma-builder/code.js` | 통과 |
| 가짜 `figma` 객체로 플러그인 전체 실행 | 완료. 변수 25개(색 16, 간격 6, 모서리 3) × 모드 2, 텍스트 스타일 12개. 모드 추가 실패 시 경고 후 계속 진행 |

## 검증하지 못한 것

- 실제 Figma 데스크톱에서 실행하지 않았다. 무료 플랜의 모드 수 제한, 기존 파일에 남은 `primitive`·`semantic` 컬렉션과 옛 텍스트 스타일 정리는 직접 확인해야 한다(플러그인은 옛 것을 지우지 않는다).
- 컴포넌트·화면 그리기 코드의 간격·모서리 숫자는 아직 변수에 바인딩하지 않았다.

## 근거 링크

- [ADR-0026](../../adr/ADR-0026-design-token-generation.md)
- [ADR-0027](../../adr/ADR-0027-typography-role-tokens.md)

## AI의 오류와 정정

- 처음 실행에서 세 페이지 빌드가 `Cannot access property children on a page that has not been explicitly loaded`로 실패했다. manifest가 `documentAccess: dynamic-page`라 페이지를 `loadAsync()`로 먼저 불러와야 하는데, 가짜 `figma` 객체 시험이 이 제약을 흉내 내지 못했다. `getOrCreatePage`를 비동기로 바꿔 `page.loadAsync()`와 `figma.setCurrentPageAsync()`를 쓰게 했다. 변수와 텍스트 스타일 단계는 경고 없이 통과했다.
- 앞의 수정 뒤에도 같은 오류가 나서 AI는 Figma가 옛 경로의 `code.js`를 실행한다고 판단했지만, 사용자가 확인한 경로는 맞았다. 판단이 틀렸다. 실행된 파일을 구분할 수 있게 알림에 빌드 표시를 넣고, 시작할 때 `figma.loadAllPagesAsync()`로 모든 페이지를 불러오게 했다. 그 뒤 페이지 빌드는 성공했다.
- 실제 Figma(무료 플랜)에서 Dark 모드 추가가 막혔고, `Roboto Mono`의 굵기 이름이 `Semi Bold`가 아니라 `SemiBold`여서 텍스트 스타일 단계가 실패했다. Dark 값은 같은 변수 이름으로 `KeyManage Dark` 컬렉션에 따로 넣고, 글꼴은 굵기 이름 표기 두 가지를 시도한 뒤 없으면 더 가는 굵기로 내려가게 했다.
