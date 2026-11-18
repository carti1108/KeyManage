# 2026-11-18 · Android 백업 화면과 목업 보강

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 7c 네 번째 PR로 목업에 백업·마스터 비밀번호 변경 단계를 더하고, Android 설정 탭에 백업 내보내기·복원 화면을 붙였다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle, Node(`node --check`)
- 플러그인·스킬: 없음

## 요청

7c-3a PR을 머지하고 7c-3b(백업·비밀번호 변경·계정 삭제)를 진행하는 요청이었다. 크기 때문에 백업(7c-3b)과 계정 삭제·비밀번호 변경(7c-3c)으로 다시 나눴다.

## AI 산출물

- `Tools/figma-builder/code.js`: 흐름 단계 `backup`·`password`와 화살표, 정책 문구, README 화면 수 갱신
- `:core:domain`: `BackupSession`(계정 KDF 값으로 내보내기, 복원 뒤 저장)
- `:feature:settings`: 백업 화면(SAF 선택, 복원 확인, VLT-004 문구), `BackupPort`·`DocumentIo`
- `:feature:main`·`:feature:root`: 백업 요청 전달, Root의 마지막 저장 볼트 추적
- `:app`: `ContentResolverDocuments`, 조립

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #144 머지 후 7c-3b 진행 |
| AI 기본값 | 새 목업의 Web 줄은 정책 문구로 그린 배치라 실제 웹 화면과 다를 수 있다고 README에 적음. 계정 삭제는 비밀번호 변경과 함께 7c-3c로 미룸. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `node --check Tools/figma-builder/code.js`, `node Tools/design-tokens/generate.mjs --check` | 통과 |
| `./gradlew test lint assembleDebug` | 통과. 백업 세션 3개, 백업 화면 6개, Root 백업 3개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. Figma에서 플러그인을 다시 실행해 새 목업을 그려 보지 않았다.
2. adb 데몬이 이 환경에서 뜨지 않아 기기에서 파일 선택·저장을 확인하지 못했다.
