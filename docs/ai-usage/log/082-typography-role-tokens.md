# 2026-11-14 · 타이포그래피 역할 토큰 도입과 웹·Apple 적용

- 이슈·PR: (이 PR)
- 커밋: (이 PR의 커밋)

> **요약** 글자 스타일을 여섯 역할(`display`·`title`·`heading`·`body`·`label`·`caption`)로 정해 `tokens.json`에 넣고(ADR-0027), 생성기가 웹 `text-<역할>` 클래스와 Apple `DesignToken.Typography.<역할>`을 만들게 했다. 웹과 Apple 화면의 기본 글자 크기를 모두 역할로 바꾸고, 다시 쓰면 막는 게이트를 넓혔다.

## 사용 도구

- 도구: Claude Code (Claude Opus 5.5)
- 플러그인·스킬: `adr-manager`

## 요청

색·간격·모서리에 이어 타이포그래피 토큰을 만들어 달라고 했다. ADR 초안(역할 6개와 대응표)을 보고 승인했다.

## AI 산출물

- `docs/adr/ADR-0027-typography-role-tokens.md`와 ADR 목록 한 줄
- `tokens.json` `typography` 그룹(DTCG `typography`, Apple 스타일은 `$extensions`), `generate.mjs`의 형식 검사와 CSS·Swift 출력
- 웹: 화면의 `text-xs`~`text-2xl`을 역할 클래스로 교체, `designLiterals.ts`에 기본 글자 크기 금지와 테스트 추가
- Apple: Features 6개와 컴포넌트 3개의 `.font(...)`를 역할 토큰으로 교체, `KMButtonSize.font` 추가, 손으로 쓴 `Typography.body` 삭제(생성 파일로 대체), `check-apple-lint.sh` 규칙 8
- `docs/design-system.md` 4.1절과 리터럴 금지 행, `Tools/design-tokens/README.md`

## 사람의 판단

- 역할 6개와 플랫폼 대응표를 승인했다.

## 화면이 달라지는 곳

- Apple `.caption`(12pt) → `caption`(`footnote`, 13pt)
- Apple 컴포넌트의 고정 크기 글자(배지 12pt, 게이지 13pt, 버튼 13/14/16pt)가 Dynamic Type을 따름
- 웹 `text-lg`·`text-xl`에 굵기를 따로 주지 않던 곳은 역할 기본 굵기(600)가 적용됨

## 검증

| 명령 | 결과 |
| :--- | :--- |
| `node Tools/design-tokens/generate.mjs --check` | 통과 |
| `npm run check` (Apps/Web) | 통과. 70개 파일 768개 테스트 |
| `npx vite build` (Apps/Web) | 통과. `.text-label{font-size:.875rem;line-height:var(--tw-leading,1.25rem);font-weight:var(--tw-font-weight,400)}` 확인 |
| `bash Scripts/check-apple-lint.sh` | 통과. 규칙 8 음성 시험에서 `.font(.footnote)`, `.font(.system(size: 13))`은 적발, `Image(systemName:)` 바로 아래 아이콘 크기는 통과 |

## 검증하지 못한 것

- Apple 빌드·테스트와 화면(Windows). `Font.system(_:design:)`·`.weight(_:)`는 iOS 13부터 있어 배포 대상(iOS 15) 안이지만 컴파일은 CI `apple` 작업에서 확인해야 한다.
- 웹 화면을 브라우저에서 눈으로 보지 않았다.

## AI의 오류와 정정

- 생성기를 고칠 때 Python 문자열 치환이 이스케이프 차이로 맞지 않아 실패했다. 편집 도구로 바꿔 다시 적용했다.

## 근거 링크

- [ADR-0027](../../adr/ADR-0027-typography-role-tokens.md)
- [디자인 시스템](../../design-system.md)
