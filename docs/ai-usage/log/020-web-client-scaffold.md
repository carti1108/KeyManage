# 2026-10-29 · 웹 클라이언트 골격과 데스크톱 틀 결정 (ADR-0012, #28)

> 요약: 데스크톱 앱이 주 사용 형태라는 방향에 맞춰 데스크톱 틀을 Tauri v2로, 웹 스택을 Vite + React + TypeScript로 정하고(ADR-0012), `Apps/Web`에 계층 격리 검사가 있는 골격을 만들었다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5), Node.js·npm, 다른 AI(Gemini)와 나눈 대화 일부를 검토 입력으로 사용
- 플러그인/스킬: `adr-manager:adr-manager`, `code-review`(Claude Code 내장, 독립 서브에이전트), `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- 웹 코드를 먼저 작성하기로 하고 프레임워크 추천을 요청받았다. 브라우저보다 데스크톱 앱을 주로 쓴다는 조건과 함께, Tauri v2 + Vite를 권한 다른 AI의 답변을 공유받았다.

## AI 산출물

- 프레임워크 비교: Vite + React + TS(서버 렌더링 없는 정적 앱에 맞음)와 Next.js 정적 내보내기, Tauri v2와 Electron.
- 다른 AI 답변 정정: 1Password 8이 Electron을 버리고 Tauri로 옮겼다는 주장은 사실과 다르고(Electron 화면 + Rust 핵심), 실행 속도·메모리 수치는 과장. 공격받을 수 있는 면 축소와 네이티브 클립보드 제어(COM-001을 데스크톱에서 지킬 수 있음)는 타당하다고 판단.
- 환경 확인: Rust와 Visual Studio C++ 빌드 도구가 없어 Tauri 빌드에 설치가 필요함을 확인. 웹 코드는 브라우저 개발 서버로 먼저 만들고 플랫폼 기능은 포트로 분리한 뒤 Tauri 틀을 나중에 붙이는 순서를 제안.
- [`docs/adr/ADR-0012-desktop-shell-tauri.md`](../../adr/ADR-0012-desktop-shell-tauri.md), design-system 웹 스택 표기 수정, open-questions 정리.
- `Apps/Web`: 공식 Vite 템플릿(Vite 8, React 19, TypeScript 7, oxlint) 기반 골격, Tailwind CSS 4, Vitest 5, 계층 구조(domain·adapters·ui·app), oxlint 계층 격리 규칙, `/api` 개발 프록시, CI `web` 작업.

## 사람의 판단

- 웹 클라이언트를 iOS보다 먼저 만들기로 했다.
- Vite + React + TypeScript를 선택하고, 데스크톱 틀로 Tauri v2를 확정했다.

## 검증

- TDD: 제목 렌더링 테스트가 템플릿 화면에서 실패함을 확인한 뒤 구현해 통과.
- `npm run check`(타입 검사, 경고도 실패로 보는 lint, 테스트)와 `npm run build` 통과.
- 계층 격리 검사: 시험 파일로 `domain`의 React·adapters·ui·app import와 브라우저 전역(window·localStorage·fetch), `ui`의 adapters·app import, `adapters`의 ui·app import를 oxlint가 모두 오류로 잡고 `globalThis.crypto`는 허용함을 확인 후 삭제.
- 독립 리뷰(`/code-review`) 9건 반영: 폴더 자체를 가리키는 import(`../adapters`)가 격리 검사를 통과하던 구멍(리뷰어가 재현), adapters 계층 규칙과 domain의 브라우저 전역 금지 추가, ui가 app을 import하지 못하게, 조립 지점에 있던 화면 마크업을 `ui/screens`로 이동, 정해지지 않은 배포 방식을 확정된 것처럼 적은 주석 수정, CI의 중복 타입 검사 제거와 머리 주석 갱신, 이 로그의 목록 등록.
- 실제 백엔드와 Vite 개발 서버를 함께 띄워, 개발 서버의 `/api/v1/vault`가 프록시를 거쳐 백엔드의 401 응답을 돌려줌을 확인. 확인 후 두 서버를 포트 기준으로 종료.

## 검증하지 못한 것

1. Tauri 틀은 아직 붙이지 않았다. Rust와 빌드 도구 설치 후 별도 이슈로 진행한다.
2. CI의 Node 22에서의 실행은 PR CI 결과로 확인한다(로컬은 Node 24).

## AI의 오류와 정정

1. 계층 격리 검사를 시험할 때 하위 경로(`../adapters/http`)만 넣어 보고, 폴더 자체(`../adapters`)를 가리키는 import가 규칙을 통과하는 구멍을 놓쳤다. 독립 리뷰가 실제로 재현해 잡았고, 두 형태를 모두 막도록 고친 뒤 다시 시험했다.
2. 개발 프록시 주석에 아직 정하지 않은 배포 방식(Caddy)을 정해진 것처럼 적었다. 리뷰 지적으로 고쳤다.

## 근거 링크

- Issue #28
- 커밋: PR 참고
