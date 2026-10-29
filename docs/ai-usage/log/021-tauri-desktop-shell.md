# 2026-10-29 · Tauri v2 데스크톱 틀 추가와 CSP 강화 (#30)

> 요약: 사용자가 Rust·빌드 도구를 설치하고 실행한 `tauri init` 결과를 바탕으로 데스크톱 틀을 정리했다. 독립 리뷰가 첫 CSP의 네트워크 허용 범위가 너무 넓다고 지적해, 네트워크를 앱 자신과 Rust(IPC)로만 제한하고 개발용 CSP를 따로 두었다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5), Tauri CLI 2, Rust 1.99, Visual Studio Build Tools 2022
- 플러그인/스킬: `code-review`(Claude Code 내장, 독립 서브에이전트), `issue-worktree:commit-rules`, `project-docs:ai-usage`

## 요청

- Rust와 빌드 도구 설치를 마쳤다는 알림. 작업 트리에 사용자가 실행한 `tauri init` 결과(`src-tauri`, `@tauri-apps/cli`)가 커밋되지 않은 채 남아 있었다.

## AI 산출물

- 설치 확인(Rust MSVC 툴체인, VS C++ 빌드 도구)과, 사용자의 미커밋 변경을 버리지 않고 새 브랜치로 옮겨 데스크톱 틀 작업으로 이어 감.
- `tauri.conf.json`: 기본값 `csp: null`을 엄격한 CSP로 교체, 개발용 `devCsp` 분리, 창 1280×800(최소 900×600), 설치 파일은 Windows NSIS만, 쓰지 않는 Android 설정 제거.
- `Cargo.toml`·`lib.rs`: 패키지 이름·설명 정리, 템플릿에 남아 있던 로그 플러그인·serde 제거.
- `vite.config.ts`: 5173 포트 고정(`strictPort`), `src-tauri` 감시 제외.
- CI `desktop` 작업: Ubuntu에서 웹 빌드 후 `cargo check --features tauri/custom-protocol`(릴리스 경로).

## 사람의 판단

- Rust와 빌드 도구를 설치하고 `tauri init`을 먼저 실행했다. 원래 화면 작업 뒤로 계획했던 데스크톱 틀을 앞당겨 정리하게 되었다.

## 검증

- 로컬 디버그 빌드 성공, 빌드된 앱과 `tauri dev` 모두 "KeyManage" 창이 뜨고 유지됨을 확인.
- `cargo check`(개발 경로)와 `cargo check --features tauri/custom-protocol`(릴리스 경로) 통과, `npm run check` 통과.
- `tauri dev` 실행 중 Vite 감시기가 Rust 빌드 결과를 감시해 나던 오류를 발견하고, 감시 제외 후 오류 0건 확인.
- 독립 리뷰(`/code-review`) 10건 반영(아래 정정 포함).

## 검증하지 못한 것

1. 데스크톱 창의 실제 화면(스타일 적용, 개발용 CSP에서의 HMR)은 사람이 직접 봐야 한다.
2. 빌드된 앱이 서버와 통신하는 방법(서버 주소 설정, Rust를 거친 요청)은 API 클라이언트 작업에서 정한다.

## AI의 오류와 정정

1. 첫 CSP에서 네트워크를 모든 HTTPS 주소와 localhost의 모든 포트로 열었다. 악성 코드가 들어오면 복호화된 볼트를 외부로 보낼 수 있어 영지식 구조를 무력화하는 설정이었다. 독립 리뷰 지적으로 앱 자신과 Rust(IPC)로만 제한했다.
2. 개발용 CSP를 두지 않아, `tauri dev`에서 Vite의 인라인 스타일·스크립트와 HMR이 막힐 수 있었다. 리뷰 지적으로 `devCsp`를 따로 두었다.
3. CI 검사가 개발 경로만 컴파일해, 빌드 결과를 앱에 담는 릴리스 경로의 오류를 놓칠 수 있었다. 리뷰 지적으로 릴리스 경로까지 검사하게 바꿨다.

## 근거 링크

- Issue #30, PR #31
