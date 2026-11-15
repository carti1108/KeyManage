# 2026-11-15 · DS Builder에 플랫폼별 화면 흐름(목업과 이동 화살표) 추가

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** Figma DS Builder가 그리던 iPhone 화면 2개를 8단계 × iPhone·Android·Web 목업 24개로 바꾸고, 화면 위에 이동 화살표를 그려 흐름대로 볼 수 있게 했다. 그리기 방식은 Git에 올라가지 않은 Flow Builder(`Tools/figma-builder/flow/`)에서 옮겨 왔고, 색은 `tokens.json`에서 만든 `KeyManage` 변수에 바인딩했다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)

## 요청

플랫폼별·화면별 UI 시안(목업)과 유저 플로우를 DS Builder로 그려 달라고 했다.

## AI 산출물

- `code.js` 6절: 플랫폼별 목업. 공용 요소(입력, 버튼, 그룹, 스위치, 슬라이더, 강도 게이지, 비밀번호 필드, 소각 배지)와 플랫폼 셸(상태 바, iPhone 탭 바, Android 내비게이션 바, Web 사이드바, 기기 틀, 브라우저 창), 8단계 화면
- Flow Builder에 없던 4단계 볼트 화면을 새로 그림(검색, 항목 목록, Web 항목 추가 버튼, Android FAB)
- 화면 사이 이동 화살표 8개(플랫폼 줄마다 반복)와 이동 이름표. 건너뛰는 화면이 많을수록 곡선이 높게 뜬다
- 이전의 iPhone 화면 2개(보안 점수 대시보드, 생성기)는 삭제
- 라이트·다크 구역을 위아래로 그림(각 24개). 무료 플랜은 다크 구역을 `KeyManage Dark` 컬렉션 변수에 바인딩하고, 모드를 쓸 수 있으면 같은 변수에 Dark 모드를 지정
- 예전 버전이 만든 `🔀 User Flow` 페이지를 실행할 때 지움
- `Tools/figma-builder/README.md` 갱신

## 사람의 판단

- 시안은 별도 플러그인이 아니라 DS Builder에 넣기로 했다.

## 바뀐 점 (Flow Builder와 비교)

- 공식 iOS·M3 키트 컴포넌트와 손으로 그린 볼트 화면에 기대지 않고 모두 직접 그린다. 키트가 없는 무료 파일에서도 같은 결과가 나온다.
- 색은 다크 고정값(`FALLBACK`) 대신 `KeyManage` 변수(Light 값)에 바인딩한다. 주황·보라 같은 브랜드 밖 색은 쓰지 않는다.

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `node --check Tools/figma-builder/code.js` | 통과 |
| `node Tools/design-tokens/generate.mjs --check` | 통과 |
| 가짜 `figma` 객체로 전체 실행(자동 레이아웃 부모 없이 `FILL`·`layoutGrow` 설정 시 실패하도록 검사) | 통과. 기기 틀·브라우저 창 48개(라이트·다크), 이동 화살표 48개. 다크 구역 바탕·화면이 `KeyManage Dark:color/background`에 바인딩됨을 확인 |

## 검증하지 못한 것

- 실제 Figma에서의 모양(글자 넘침, 화살표·이름표가 겹치는지)은 사용자가 실행해 확인해야 한다.

## AI의 오류와 정정

- 처음에는 "유저 플로우"를 화면과 별개인 상자·화살표 순서도(`🔀 User Flow` 페이지)로 그렸다. 사용자가 원한 것은 실제 목업 화면을 흐름대로 늘어놓고 화살표로 잇는 화면 흐름이어서, 순서도 페이지를 지우고 목업 페이지에 이동 화살표를 그렸다.

- 처음 시험 실행에서 볼트 화면이 `layoutGrow`를 부모에 붙이기 전에 설정해 실패했다. 순서를 바꿨다.

## 근거 링크

- [ADR-0025](../../adr/ADR-0025-design-system-shared-contract-platform-expression.md)
- [디자인 시스템](../../design-system.md)
