# 2026-11-18 · Android 계정 삭제와 마스터 비밀번호 변경

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 7c 마지막 PR로 설정 탭에 마스터 비밀번호 변경(SEC-004)과 계정 삭제(SEC-005)를 붙였다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

7c-3b PR을 머지하고 7c-3c(계정 삭제·비밀번호 변경)를 진행하는 요청이었다.

## AI 산출물

- `:core:domain`: `AccountUseCase`·`AccountError`, `VaultSyncCoordinator.expectedRevision`·`adoptPasswordChange`
- `:feature:settings`: 비밀번호 변경·계정 삭제 화면, `AccountPort`, 문구(SEC-002·SEC-004·SEC-005)
- `:feature:main`·`:feature:root`: 요청 전달, 키·세션 교체, 삭제 뒤 로그인 화면 안내
- `:app`: 동기화 조정자를 세션·계정 유스케이스가 함께 쓰도록 조립

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #145 머지 후 7c-3c 진행 |
| AI 기본값 | 서버에 보내기 전에 입력 비밀번호를 기기 키와 대조함. `SettingsListener`에 두 콜백이 늘어 기존 `MainInteractorTest`의 익명 listener를 고친 변경은 별도 `test:` 커밋. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 계정 유스케이스 7개, 설정 화면 6개, Root 3개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 실제 백엔드에 비밀번호 변경·계정 삭제를 보내 보지 않았다.
2. adb 데몬이 이 환경에서 뜨지 않아 기기에서 화면을 확인하지 못했다.
