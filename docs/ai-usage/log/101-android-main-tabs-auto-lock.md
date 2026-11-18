# 2026-11-18 · Android 탭 구조·생성기·설정과 자동 잠금

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 7c 세 번째 PR로 잠금이 풀린 뒤의 탭 구조(볼트·생성기·설정)와 생성기 화면, 설정 화면(자동 잠금 선택, 대기 중 확인을 거치는 로그아웃), 자동 잠금 타이머를 넣었다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

7c-2 PR을 머지하고 7c-3을 진행하는 요청이었다. 7c-3은 탭·생성기·설정·자동 잠금(7c-3a)과 백업·비밀번호 변경·계정 삭제(7c-3b)로 나눴다.

## AI 산출물

- `:core:domain`: `AutoLock`·`AutoLockOption`·`AutoLockSettingStore`, `VaultSessionUseCase.hasPendingChanges`
- `:core:data`: `PreferencesAutoLockStore`
- `:feature:generator`, `:feature:settings`, `:feature:main`(interface·implementation·testing·tests)
- `:feature:root`: Main 연결(`showMain`), `RootAutoLock`, `AppEvents`
- `:app`: Activity 생명주기·조작 전달, 조립

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #143 머지 후 7c-3 진행 |
| AI 기본값 | LoggedIn RIB을 따로 두지 않고 Root가 키 보관·잠금을 맡음. 탭을 바꾸면 이전 탭 RIB을 떼고, 볼트 탭은 Main이 들고 있는 최신 볼트로 다시 만듦. `RootRouting` 이름 변경에 따른 기존 Root 테스트 수정은 별도 `test:` 커밋. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 자동 잠금 7개, 생성기 5개, 설정 4개, Main 2개, Root 자동 잠금 4개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. adb 데몬이 이 환경에서 뜨지 않아 백그라운드 전환·자동 잠금을 기기에서 확인하지 못했다.
2. 생체 인증(SEC-003 규칙 4·8)은 아직 없다.
