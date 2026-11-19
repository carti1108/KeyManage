# 2026-11-19 · Android 생체 해제 판정과 Keystore 키 보관

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 8a 첫 PR로 생체 해제 판정(SEC-003 규칙 4·8, 14일 재요구)과 볼트 암호화 키를 Keystore 생체 전용 키로 감싸는 어댑터를 만들었다. 화면 연결은 다음 PR에서 한다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음(다른 플랫폼 조사는 탐색 서브에이전트)

## 요청

생체 인증 잠금 해제를 먼저 진행하되, Apple처럼 볼트 암호화 키를 감싸는 방식으로 하라는 요청이었다.

## AI 산출물

- `Apps/Android/docs/adr/ADR-AND-002`: 볼트 키를 Keystore AES-256-GCM 키(매번 강한 생체 인증, 생체 등록 시 무효)로 감싸고 `androidx.biometric` 1.1.0으로 인증
- `:core:domain`: `BiometricUnlock`·`BiometricError`·`BiometricKeyStore`·`BiometricSettingStore`, `VaultSessionUseCase.unlockWithKey`
- `:core:data`: `KeystoreBiometricKeyStore`, `AndroidKeystoreKeys`, `PreferencesBiometricSettingStore`

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | 다른 플랫폼 현황을 먼저 확인한 뒤 Apple과 같은 볼트 키 방식 선택 |
| AI 기본값 | 생체 해제는 기기 볼트만 열고 재로그인하지 않음. 14일이 지나면 키를 남긴 채 비밀번호 요구. `androidx.biometric` 의존성은 실제로 쓰는 화면 PR에서 추가. 기존 `VaultSessionUseCaseTest`에 더한 테스트는 별도 `test:` 커밋. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 생체 판정 10개, 볼트 키 잠금 해제 3개, Keystore 어댑터 7개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. `AndroidKeystoreKeys`는 JVM 테스트로 돌릴 수 없어 실기기에서 키 생성·무효화를 확인하지 못했다(adb 사용 불가).
2. 다른 플랫폼 조사에서 Apple도 생체 키 저장 구현이 아직 없다는 점을 확인했다(인터페이스와 판정만 있음).
