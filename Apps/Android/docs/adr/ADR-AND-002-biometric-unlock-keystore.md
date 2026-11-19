### ADR-AND-002 Android 생체 해제는 볼트 암호화 키를 Keystore 생체 전용 키로 감싸 기기에 두고, androidx.biometric으로 인증한다
- 상태: 채택
- 날짜: 2026-11-19
- 영역: Android

> **요약** 생체 인증을 켜면 메모리의 볼트 암호화 키를 Android Keystore의 AES-256-GCM 키로 감싸 앱 전용 파일에 둔다. Keystore 키는 강한 생체 인증을 통과할 때마다만 쓸 수 있고, 생체 정보가 새로 등록되거나 기기 암호를 끄면 무효가 된다. 인증 화면은 `androidx.biometric` 1.1.0의 `BiometricPrompt`와 `CryptoObject`로 띄운다.

#### 배경

[SEC-003](../../../../docs/policies/SEC/SEC-003-unlock-and-auto-lock.md) 규칙 4·8과 값 표는 생체 해제 키를 이 기기 전용 저장소에 두고 생체 인증으로 보호하며, 기기 암호가 있을 때만 쓰게 하라고 정한다. Android 구현에서 정할 것은 두 가지였다.

- 무엇을 감쌀지. Apple은 `BiometricKeyStoreProtocol`이 볼트 암호화 키를 돌려주는 구조다(`Apps/Apple/Projects/Features/Unlock/Interface/BiometricKeyStoreProtocol.swift`).
- 어떤 API로 인증할지. 최소 SDK는 26인데 플랫폼 `android.hardware.biometrics.BiometricPrompt`는 28부터 있다.

#### 결정

1. **볼트 암호화 키를 감싼다**: 마스터 키나 비밀번호는 기기에 두지 않는다. 생체 해제는 기기 볼트 복호화만 하고, 인증 해시가 없으므로 서버 재로그인은 하지 않는다(메모리에 세션이 남아 있으면 그대로 쓴다).
2. **Keystore 키 속성**: `KeyGenParameterSpec`에 `setUserAuthenticationRequired(true)`, `setInvalidatedByBiometricEnrollment(true)`, 인증 유효 시간 0(매번 인증, API 30 이상은 `AUTH_BIOMETRIC_STRONG`), 블록 GCM·패딩 없음을 준다. 감싼 결과(IV·암호문)는 `noBackupFilesDir`에 두어 백업·동기화에서 뺀다.
3. **무효화 처리**: 키를 쓰다 `KeyPermanentlyInvalidatedException`이나 키 없음이 나면 감싼 파일과 Keystore 키를 지우고 생체 인증을 끈 뒤 마스터 비밀번호를 요구한다(규칙 8).
4. **판정은 Domain**: 켜짐 여부, 마지막 마스터 비밀번호 해제 시각, 14일 재요구는 `:core:domain`의 순수 Kotlin으로 두고 Apple `VaultLockManager`와 같은 규칙을 쓴다. Keystore·`BiometricPrompt`는 `:core:data`·`:app`에만 둔다.
5. **볼트 키가 바뀌면 끈다**: 로그아웃·계정 삭제·마스터 비밀번호 변경 때 감싼 키를 지운다.
6. **의존성**: `androidx.biometric:biometric:1.1.0`(현재 마지막 안정판).

#### 대안

| 대안 | 고르지 않은 이유 |
| :--- | :--- |
| 마스터 키를 감쌈 | 기기에 남는 값이 인증 해시까지 만들 수 있어 권한이 넓어진다. Apple과도 다르다. |
| 플랫폼 `BiometricPrompt`만 사용 | API 26·27 기기에서 생체 해제를 쓸 수 없다. |
| 인증 유효 시간을 둔 키 | 인증 뒤 일정 시간 키가 열려 있어 규칙 8의 의도(쓸 때마다 생체 확인)와 맞지 않는다. |

#### 결과

- 생체 정보 변경 감지를 Keystore가 맡아 Apple의 생체 상태 해시 비교가 필요 없다.
- 기기 암호를 끄면 OS가 키를 지우므로 별도 감시가 필요 없다.
- `androidx.biometric` 의존성과 `FragmentActivity` 호스트가 필요하다.
