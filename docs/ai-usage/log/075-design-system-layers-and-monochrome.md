# 2026-11-11 · 디자인 시스템 공통 계약층·플랫폼 표현층 분리와 흑백 팔레트

- 이슈·PR: 없음(PR 생성 전)
- 커밋: b3b7516, ad2f253, 153c24a, bf1981a, 7546754, 2b21660

> **요약** 디자인 시스템을 공통 계약층(토큰 이름·컴포넌트 계약)과 플랫폼 표현층(Liquid Glass·Material 3)으로 나누는 ADR-0025를 쓰고 문서를 개편했다. 이어서 색상을 흑백과 초록·빨강 신호색으로 바꿔 문서, 웹, Apple 토큰에 반영했다. 시안은 Claude Design과 Figma로 만들었다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인·스킬: `adr-manager`, `ai-usage`, Figma MCP(`figma-use`, `figma-generate-library`), Claude Design(Design System·Design 아티팩트)

## 요청

현재 디자인 시스템이 갖춰져 있는지 점검하고, 공통 UI 컴포넌트를 관리하는 경험을 해 보고 싶다는 요청에서 시작했다. 공통 컴포넌트라도 Apple은 Liquid Glass, Android는 Material 3처럼 각 플랫폼의 디자인 언어를 따라야 한다는 방향을 사람이 제시했다. 이후 플랫폼별 화면 흐름, 기기 목업, 공식 키트 적용, 흑백 팔레트 전환을 차례로 요청했다.

## AI 산출물

- `docs/adr/ADR-0025-design-system-shared-contract-platform-expression.md`: 네 개의 층(브랜드·의미, 컴포넌트 계약, 표현, 네이티브 컨트롤)으로 나누는 결정과 대안 4가지.
- `docs/design-system.md`: 층 구조, 간격·모서리 토큰, 플랫폼 표현 규칙, Alert·ConfirmPrompt·ModalDialog 계약과 구현 현황 표 추가. 이후 색 표를 흑백 팔레트로 변경.
- `Apps/Web/src/index.css`: 라이트·다크 색 토큰을 흑백 팔레트로 변경.
- `Apps/Apple/Projects/Core/DesignSystem/`: `DesignTokenTests` 기대값을 먼저 바꾸고 `DesignToken` 색 값을 맞춤.
- 레포 밖 산출물: Claude Design 디자인 시스템(토큰·컴포넌트 8종 미리보기)과 캔버스(컴포넌트 보드, 흑백 시안 보드), Figma 파일(변수, 컴포넌트 8종, 플랫폼별 볼트 화면).
- 로컬 탐색용 Figma 플러그인 `Tools/figma-builder/flow`, `Tools/figma-builder/inventory`(커밋하지 않음).

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 플랫폼 디자인 언어 존중 | 공통 컴포넌트라도 표현은 각 OS를 따라야 한다는 방향을 사람이 정했고, AI가 이를 층 구조로 정리했다. |
| ADR 기본값 수용 | Android 다이내믹 컬러 미사용, 데스크톱은 웹 표현을 따르는 기본값을 그대로 받아들였다. |
| Figma 다크 전용 진행 | 무료 플랜의 변수 모드 1개 제한을 확인한 뒤 다크 모드만으로 진행하기로 했다. |
| 공식 키트 활용 방식 | 키트 컴포넌트를 전부 옮기자는 안내를 거부하고, 필요한 것만 Assets에서 놓은 뒤 키를 뽑아 플러그인이 불러오는 방식으로 바꿨다. |
| 흑백 팔레트 | 강조색은 초록·빨강만 남기고 나머지는 블랙·화이트로 통일하되 Liquid Glass·Material 3 모양은 유지하도록 정했다. |
| iOS 모드 정책 보류 | iOS가 시스템 라이트·다크를 따르고 화면이 배경 토큰을 쓰지 않는다는 점을 확인했지만, 정책 결정은 미루고 현재 상태로 PR을 올리기로 했다. |

## AI의 오류와 정정

- Claude Design 캔버스의 Apple·Android 칸을 처음에 공식 리소스를 쓴 것처럼 소개했으나, 실제로는 기억에 기댄 근사치였다. 질문을 받고 정정했다.
- Figma MCP로 그릴 때 변수에 연결한 채움의 투명도가 빠지는 문제, 오토레이아웃 크기가 고정되는 문제가 여러 번 생겨 스크린샷으로 확인한 뒤 고쳤다.
- 무료 플랜의 MCP 호출 한도를 문서상 명시가 없다는 이유로 낙관했다가 작업 중간에 한도에 걸렸다. 이후 로컬 플러그인 방식으로 우회했다.
- 키트 목록 추출 플러그인 안내를 처음에 너무 넓게 해서(키트 컴포넌트 전체를 옮기는 것처럼 읽힘) 사람이 지적했다.
- 테스트 기대값과 구현을 한 커밋에 넣으려다 TDD 무결성 훅에 막혔고, 프로젝트 규칙대로 `test:`와 `feat:` 커밋으로 나눴다.

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `bash Scripts/check-docs.sh` | 통과 |
| `bash Scripts/check-policy-acceptance.sh` | 통과 |
| `bash Scripts/check-apple-lint.sh`(pre-commit) | 통과 |
| 웹·Apple 토큰 값 일치 비교 스크립트 | 15개 색 토큰 모두 일치 |
| Figma 컴포넌트·화면 스크린샷 확인 | MCP로 그린 부분만 확인 |
| 웹 테스트, Xcode 빌드·`DesignTokenTests` 실행 | 하지 않음 |

## 검증하지 못한 것

- Xcode 빌드와 `DesignTokenTests` 실행(Windows 환경). Mac에서 확인이 필요하다.
- 웹 화면에서 흑백 팔레트가 실제로 어떻게 보이는지.
- 로컬 Figma 플러그인 실행 결과 일부(공식 키트 기반 Flow Builder, 팔레트 동기화).
- iOS 화면은 배경 토큰을 쓰지 않아 흑백 팔레트가 화면 배경에 반영되지 않는다.

## 근거 링크

- 커밋: b3b7516, ad2f253, 153c24a, bf1981a, 7546754, 2b21660
