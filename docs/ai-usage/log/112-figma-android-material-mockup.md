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
- `Apps/Android/docs/platform-bindings.md`, `Tools/figma-builder/README.md`: 목업 Android 줄이 M3 규격이라는 설명

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | MCP 없이 스크립트만 고치는 방식으로 진행 |
| AI 기본값 | Google의 Material 3 Design Kit을 불러오는 방식은 라이브러리 게시가 필요해 쓰지 않고 스크립트가 직접 그리게 했다. iPhone·Web 줄은 건드리지 않았다. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `KMIcons` 7개와 google/material-design-icons 원본 24px SVG 대조 | 모두 일치. 앱 수정 없음 |
| `node --check Tools/figma-builder/code.js` | 통과 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. Figma에서 플러그인을 실행하지 못했다. 그려진 결과는 사람이 Figma 데스크톱 앱에서 실행해 확인해야 한다.
2. `createNodeFromSvg`로 만든 아이콘에 색 변수를 바인딩하는 부분과 `rescale`은 실행해 보지 않은 코드다.
