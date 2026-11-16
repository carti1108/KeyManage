# 2026-11-14 · 웹 화면 간격·색 리터럴을 토큰으로 맞추고 검사 스크립트 추가

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** 웹 화면 코드의 Tailwind 클래스에서 척도 밖 간격(반 단계, 8)과 기본 팔레트 색(`bg-black/40`)을 토큰으로 맞췄다. 모달 뒤 막을 위해 `overlay` 색 토큰을 추가했다. `Apps/Web/scripts/designLiterals.ts`가 `npm run check`에서 돌아 같은 리터럴이 다시 들어오면 실패한다(ADR-0025 결정 7). Apple 규칙 6·7과 같은 수준이다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)

## 요청

Apple에 이어 웹 화면의 리터럴 점검과 금지 게이트를 진행해 달라고 했다.

## AI 산출물

- `Apps/Web/scripts/designLiterals.ts`와 테스트 5개, `package.json`의 `check`에 연결 (CI `web` 작업이 `npm run check`를 돈다)
- 화면 15개 파일의 간격 클래스 교체, `ModalDialog`의 `bg-black/40` → `bg-overlay`
- `tokens.json`에 `overlay`(라이트·다크 모두 `rgba(0, 0, 0, 0.4)`)를 추가하고 생성 파일 갱신
- `docs/design-system.md` 2장 색 표에 `--overlay`, 1장 리터럴 금지 행에 웹 검사 위치 추가

## 척도 맞춤 규칙

| 원래 | 바뀐 값 |
| :--- | :--- |
| `*-0.5`, `*-1.5`, `*-2.5`, `*-3.5` | 한 단계 위 정수(1, 2, 3, 4) |
| `p-8`, `py-8` | `p-6`, `py-6` |
| `VaultHomeScreen`의 `lg:p-8` | 지움 (`md:p-6`이 그대로 적용) |

## 사람의 판단

- 반 단계는 Apple의 척도 맞춤(같으면 큰 쪽)과 같은 규칙으로 정했다. 되돌리기 쉬운 UI 결정이라 AI가 정하고 보고했다.
- 검사는 oxlint가 사용자 정의 정규식 규칙을 지원하지 않아 저장소의 `scripts/*.ts` 관례대로 Node 스크립트로 만들었다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `npm run check` (Apps/Web) | 통과. 리터럴 검사 통과, 70개 파일 767개 테스트 통과 |
| `npx vite build` (Apps/Web) | 통과. 빌드된 CSS에 `.bg-overlay{background-color:var(--overlay)}` 확인 |
| `node Tools/design-tokens/generate.mjs --check` | 통과 |

## 검증하지 못한 것

- 간격이 반 단계씩 넓어진 화면을 브라우저에서 눈으로 확인하지 않았다.
- `overlay` 토큰은 Apple 생성 파일에도 들어가지만 Apple 화면에서는 아직 쓰지 않는다(시스템 시트의 막을 쓴다).

## 근거 링크

- [ADR-0025](../../adr/ADR-0025-design-system-shared-contract-platform-expression.md)
- [ADR-0026](../../adr/ADR-0026-design-token-generation.md)
