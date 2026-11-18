# 2026-11-18 · Android 잠금 해제와 볼트 목록

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 7단계 두 번째 PR로 로그인 뒤 볼트 열기, 마스터 비밀번호 잠금 해제, 볼트 목록·검색, 수동 잠금, 앱 전환 화면 가림을 넣었다. Root가 Auth·Unlock·VaultList를 오가며 암호화 키를 쥔다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

7a PR을 머지하고 7b(잠금 해제·볼트 목록)를 진행하는 요청이었다.

## AI 산출물

- `:core:domain`: `VaultSessionUseCase`(인증 뒤 볼트 열기, 잠금 해제, 로그아웃), `OpenedVault`, `UnlockError`, `ItemList`(웹 `listItems`와 같은 정렬·검색), `VaultSyncCoordinator.latestVault`·`hasPending`·`keepServerCopy`
- `:feature:unlock`, `:feature:vaultlist`(interface·implementation·testing·tests)
- `:feature:root`: 화면 전환(`RootRouting`), 키 보관과 잠금, 안내 띠, `VaultSessionGateway`
- `:app`: 세션 유스케이스 조립, 기기 기록 파일(`noBackupFilesDir`), `FLAG_SECURE`

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #139 머지 후 7b 진행 |
| AI 기본값 | 자동 잠금 타이머·생체 인증·복사·탭 구조(LoggedIn/Main)는 7c로 미룸. 7a의 임시 계정 표시를 없애며 그 테스트(`RootAuthTest`)를 지우고 `RootFlowTest`로 바꿈(TDD 게이트는 `ALLOW_TEST_EDIT=1`로 통과). |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 세션 13개, 목록 2개, Unlock 7개, VaultList 3개, Root 7개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. adb 데몬이 이 환경에서 뜨지 않아 에뮬레이터와 로컬 백엔드로 화면 흐름을 확인하지 못했다.
2. 대기 중 로그아웃 확인(VLT-002 규칙 11)과 오프라인 배너(규칙 1)는 아직 없다.
