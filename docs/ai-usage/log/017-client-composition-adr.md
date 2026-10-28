# 2026-10-28 · 클라이언트 구성 결정: iOS 네이티브와 웹 코드 공유 (ADR-0010)

> 요약: 플랫폼별 와이어프레임을 검토하다 Web 화면이 데스크톱 앱 형태라는 점에서 출발해, 1Password처럼 웹 코드 하나를 브라우저와 데스크톱 앱이 함께 쓰는 방식을 채택했다. iOS와 웹을 먼저 만들고, macOS 방식은 나중에 정하며, Android는 마지막(선택)으로 둔다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5), Figma(사용자 실행)
- 플러그인/스킬: `adr-manager:adr-manager`, `project-docs:ai-usage`

## 요청

- 와이어프레임의 Web 화면이 웹이 아니라 데스크톱 앱 형태로 보인다는 점, 실제로 받아 본 1Password가 데스크톱 앱이라는 점을 짚으며 방향을 물었다.

## AI 산출물

- 1Password의 제품 형태 정리(데스크톱 앱, 웹 앱, 브라우저 확장, 모바일 앱)와, 데스크톱 앱과 웹 앱이 사실상 같은 화면이라는 설명.
- 현재 계획(iOS·Android·macOS 네이티브 + 브라우저 웹)과 1Password 방식(웹 코드 하나로 브라우저·데스크톱) 비교, 1Password 방식 추천.
- [`docs/adr/ADR-0010-client-composition-ios-and-shared-web.md`](../../adr/ADR-0010-client-composition-ios-and-shared-web.md) 작성, open-questions 2건 등록(macOS 구현 방식, 데스크톱 앱 틀).

## 사람의 판단

- 1Password 방식을 채택했다.
- 구현 순서를 iOS와 웹 먼저로 정하고, macOS를 네이티브로 할지 웹 코드로 할지는 나중에 정하기로 했다.
- Android는 네이티브로 하되 가장 마지막이며 만들지 않을 수도 있다고 정했다.
- 형태 확인용 와이어프레임 작업은 PR 없이 로컬에서만 진행하도록 지시하고, 이미 연 와이어프레임 PR을 닫았다.

## 검증

- `bash Scripts/check-docs.sh`: **문서 검사 통과**.
- 코드 변경이 없어 빌드·테스트는 하지 않았다.

## 근거 링크

- 커밋: PR 참고
