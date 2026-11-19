# 2026-11-19 · Android 생체 해제 화면 연결

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 8a 두 번째 PR로 잠금 화면 생체 해제 버튼, 설정 탭 생체 인증 스위치, `BiometricPrompt` 조립을 붙이고 로그아웃·계정 삭제·비밀번호 변경 때 생체 해제를 끄게 했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

8a-1 PR을 머지하고 생체 해제 화면 연결(8a-2)을 진행하라는 요청이었다.

## AI 산출물

- `:feature:unlock`: `BiometricUnlocker` 포트, 생체 해제 버튼과 실패 문구
- `:feature:settings`: `BiometricPort`, 생체 인증 스위치
- `:feature:main`·`:feature:root`: 스위치 요청을 볼트 키를 쥔 Root로 전달, `BiometricGateway`
- `:app`: `androidx.biometric` 1.1.0, `BiometricPrompt` 인증기, 마스터 비밀번호 해제·로그인 시각 기록, 로그아웃·계정 삭제·비밀번호 변경 뒤 끄기

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #148 머지 후 8a-2 진행 |
| AI 기본값 | Android 생체 해제 문구는 SEC-003 문구 표(iOS Face ID만 있음)를 바꾸지 않고 플랫폼 바인딩 문서에 둠. 인증 창을 자동으로 띄우지 않고 버튼으로 시작. 가입 직후 켤지 묻는 흐름(SEC-003 값 표)은 다음 PR로 미룸. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 잠금 화면 5개, 설정 5개, Root 3개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 실기기에서 `BiometricPrompt`·Keystore 키 무효화를 확인하지 못했다(adb 사용 불가).
2. 가입 직후 생체 인증을 켤지 묻는 흐름은 아직 없다.
