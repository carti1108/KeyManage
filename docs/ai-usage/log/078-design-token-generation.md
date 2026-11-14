# 2026-11-14 · 디자인 토큰 JSON 원본과 웹·Apple 코드 생성기 도입

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** 색·간격·모서리 토큰 값을 `Tools/design-tokens/tokens.json` 한 곳에 두고, 외부 의존성 없는 Node 생성기로 웹 CSS와 Apple Swift 파일을 만들게 했다(ADR-0026). CI와 pre-commit이 `--check`로 원본과 생성 파일의 일치를 검사한다. 기존 값은 바뀌지 않았다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인·스킬: `adr-manager`, `ai-usage`

## 요청

외부 디자인 시스템 사례(Buffer, 당근 SEED, 토스)를 살펴본 뒤, 색 값이 문서·`index.css`·`DesignToken.swift` 세 곳에 따로 적힌 문제를 풀 토큰 생성 구조를 먼저 만들어 달라고 요청했다.

## AI 산출물

- `docs/adr/ADR-0026-design-token-generation.md`와 ADR 목록 한 줄
- `Tools/design-tokens/`: `tokens.json`(DTCG 형식), `generate.mjs`(형식 검사, 생성, `--check`), `README.md`
- 생성 파일: `Apps/Web/src/styles/tokens.css`, `Apps/Apple/Projects/Core/DesignSystem/Sources/Generated/DesignToken+Generated.swift`(`Colors`·`Spacing`·`Radius`)
- `Apps/Web/src/index.css`, `DesignToken.swift`: 손으로 쓴 값을 지우고 생성 파일을 쓰도록 변경
- `.github/workflows/ci.yml`의 `design-tokens` 작업, `.githooks/pre-commit`의 토큰 검사 단계
- `docs/design-system.md` 2·3장: 값 열을 지우고 `tokens.json`을 가리키도록 변경

## 사람의 판단

- Style Dictionary와 자체 생성기를 비교한 ADR 초안을 보고 자체 생성기 안을 승인했다.
- Feature 코드 리터럴 교체와 리터럴 금지 게이트는 이번 범위에서 뺐다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `node Tools/design-tokens/generate.mjs --check` | 통과 |
| `bash Scripts/check-docs.sh` | 통과 |
| `npx vite build` (Apps/Web) | 통과. 빌드된 CSS에 `--accent:#059669`·`#10b981`, `--space-3:12px`, `.rounded-lg{border-radius:12px}` 확인 |
| `npx vitest run` (Apps/Web) | 69개 파일, 762개 테스트 통과 |
| `bash Scripts/check-apple-lint.sh`, `bash Scripts/check-swift-architecture.sh` | 통과 |

## 검증하지 못한 것

- Windows 환경이라 Apple 빌드와 단위 테스트(Tuist, Xcode)는 돌리지 못했다. CI의 `apple` 작업에서 확인해야 한다.

## AI의 오류와 정정

- `index.css`가 CRLF 줄바꿈이라 처음 편집에서 잘라낼 위치를 찾지 못해 파일 앞부분이 중복됐다. 파일을 되돌리고 줄바꿈을 맞춰 다시 편집했다.

## 근거 링크

- [ADR-0026](../../adr/ADR-0026-design-token-generation.md)
- [디자인 시스템](../../design-system.md)
