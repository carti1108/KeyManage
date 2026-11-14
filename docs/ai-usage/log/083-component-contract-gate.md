# 2026-11-14 · 컴포넌트 계약 게이트 추가와 문서 어긋남 정정

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** ADR-0025 결정 7의 마지막 게이트로, `docs/design-system.md` 6.1절 구현 현황 표와 웹·Apple 컴포넌트 구현을 양방향으로 대조하는 `Tools/design-tokens/check-components.mjs`를 추가했다. 처음 실행에서 문서와 코드의 어긋남 두 건을 찾아 문서를 고쳤다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)

## 요청

디자인 시스템 남은 작업 중 다음 단계(컴포넌트 계약 게이트)를 진행해 달라고 했다.

## AI 산출물

- `check-components.mjs`: 표의 `구현`은 웹 `components/<이름>.tsx`의 `export function`, Apple `public struct KM<이름>`이 있어야 하고, `미구현`은 없어야 한다. 표에 없는 웹 컴포넌트 파일이나 Apple `KM` 타입이 있으면 실패한다. 구현 이름이 규격과 다르면 `구현 (`실제이름`)`으로 적는다.
- CI `design-tokens` 작업과 `.githooks/pre-commit` 1-2단계에 연결
- 계약 형식은 새 파일을 만들지 않고 기존 6.1절 표를 그대로 원본으로 썼다(문서 주도 개발).

## 찾은 어긋남과 정정

| 어긋남 | 정정 |
| :--- | :--- |
| 표의 `PasswordInputField`가 웹에서는 `PasswordField`라는 이름으로 구현돼 있었다 | 칸을 `구현 (`PasswordField`)`로 바꿈 |
| 웹 `TextField`가 계약 표와 명세에 없었다 | 표에 행을 추가하고 6.10절 명세를 새로 씀(Apple은 시스템 `TextField` 사용) |

## 사람의 판단

- 계약 표 형식은 되돌리기 쉬운 결정이라 AI가 기존 표를 확장하는 방식으로 정하고 보고했다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `node Tools/design-tokens/check-components.mjs` | 통과 (9개) |
| 음성 시험: 표에 없는 `Foo.tsx` 추가, `KMAlert`을 `구현`으로 표기 | 둘 다 적발 |
| `bash Scripts/check-docs.sh` | 통과 |

## 근거 링크

- [ADR-0025](../../adr/ADR-0025-design-system-shared-contract-platform-expression.md)
- [디자인 시스템](../../design-system.md)
