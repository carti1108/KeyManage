# 2026-11-18 · Android 로그인·가입 화면

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 7단계 첫 PR로 Android 인증 유스케이스와 로그인·가입 RIB(`:feature:auth`)을 넣고, Root가 Auth를 붙였다 떼도록 이었다. 볼트 화면(LoggedIn)은 다음 PR이다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

6d PR을 머지하고 7단계(화면)를 진행하는 요청이었다. 7단계는 크기 때문에 Auth(7a), Unlock·LoggedIn·볼트 목록(7b), 항목·생성기·설정(7c)으로 나눴다.

## AI 산출물

- `:core:domain`: `AuthUseCase`(이메일 정규화, 새 비밀번호 검사, 가입·로그인, 인증 해시만 전송), `AuthResult`, `AuthError`
- `:feature:auth`(interface·implementation·testing·tests): Buildable·Dependency·Listener, Interactor·Presenter·View·ViewState/ViewAction, SEC-002 문구 매핑, 테스트 더블
- `:feature:root`: `RootDependency.authBuildable`, Router의 Auth 붙이기·떼기, 인증 뒤 임시 계정 표시
- `:app`: `BuildConfig.SERVER_URL`과 실제 포트 구현 조립

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #138 머지 후 7단계 진행 |
| AI 기본값 | 7단계를 세 PR로 나눔. 로그인과 가입은 입력 칸만 달라 한 RIB의 두 모드로 둠. 가입 강도 게이지는 GEN-001 구현과 함께 미룸. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 인증 유스케이스 10개, Auth Interactor 8개, Root 1개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 이 환경에서 adb 데몬이 뜨지 않아 에뮬레이터에서 화면과 로컬 백엔드 로그인을 확인하지 못했다.
2. 인증 뒤에는 임시 계정 표시만 있다. 볼트 열기는 7b에서 붙인다.
