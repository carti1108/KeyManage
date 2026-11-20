# 2026-11-20 · Figma 목업 Android 줄을 Material 3 모양으로 수정

- 이슈·PR: 없음
- 커밋: 없음(커밋 전)

> **요약** 목업 플러그인이 Android 줄을 M3 규격과 실제 Material Icons로 그리도록 고치고, 앱의 `KMIcons` 경로를 원본과 대조했다.

## 사용 도구

- 도구: Claude Code(Fable 5.2), Node(문법 검사), curl(Material Icons 원본 조회)
- 플러그인·스킬: 없음

## 요청

#154에서 Android 화면을 Material 3로 정리한 뒤, Figma 목업의 Android 줄도 MCP 없이 같은 모양으로 고치라는 요청이었다.

## AI 산출물

- `Tools/figma-builder/code.js`: `MATERIAL_ICONS`(경로 8개)와 `mIcon`·`mIconButton`·`m3Field`를 추가했다. Android 줄의 입력 칸은 이름표가 칸 안에 있는 M3 Filled 필드, 위쪽 바는 높이 64의 `TopAppBar`, 아래 탭은 아이콘이 있는 `NavigationBar`, 계정 삭제는 스크림 위 `AlertDialog`다. 볼트 목록 줄의 복사와 추가 버튼, 잠금 화면 자물쇠도 아이콘으로 바꿨다
- `Tools/figma-builder/code.js` Web 줄: 백업·비밀번호 변경 단계의 Web 화면 함수가 없어 두 화면이 그려지지 않던 것을 웹 대화상자 문구로 추가했다. `resize()`가 자동 배치 프레임의 두 축을 고정해 버튼 너비와 카드 높이가 10으로 찌그러지던 것도 고쳤다
- `Apps/Android/docs/platform-bindings.md`, `Tools/figma-builder/README.md`: 목업 Android 줄이 M3 규격이라는 설명

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | MCP 없이 스크립트만 고치는 방식으로 진행 |
| 교정 | Android 줄만 고치고 끝냈으나, Web 줄에 빠진 화면이 있다는 지적을 받고 Figma를 열어 확인해 원인을 찾았다. |
| AI 기본값 | Google의 Material 3 Design Kit을 불러오는 방식은 라이브러리 게시가 필요해 쓰지 않고 스크립트가 직접 그리게 했다. iPhone·Web 줄은 건드리지 않았다. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `KMIcons` 7개와 google/material-design-icons 원본 24px SVG 대조 | 모두 일치. 앱 수정 없음 |
| `node --check Tools/figma-builder/code.js` | 통과 |
| `bash Scripts/check-docs.sh` | 통과 |
| 사람이 플러그인을 실행한 뒤 Figma를 브라우저로 열어 라이트 구역 확인 | Android 줄 10개와 Web 줄 10개가 그려짐. 첫 실행에서 찾은 문제(Android 계정 삭제 스크림, Web 누락 화면·찌그러진 버튼)는 재실행 뒤 고쳐짐 |

## 검증하지 못한 것

1. 다크 구역은 전체 보기로 화면 수만 확인했다.
2. 브라우저 캡처 해상도가 낮아 글자 단위로는 확인하지 못했다.
