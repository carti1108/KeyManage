# Android 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: `Apps/Android/core/domain/.../crypto`(포트, 전처리, KDF 파라미터 검사), `Apps/Android/core/data/.../crypto`(PBKDF2·HKDF·AES-GCM 구현)

> **요약** Android 클라이언트를 만들 때 쓸 라이브러리와 이름을 미리 적어 둔다. 공통 명세에 있던 Android 항목을 옮긴 것이다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2 | `Mac.getInstance("HmacSHA256")`로 RFC 8018 블록 1개를 직접 계산한다. `SecretKeyFactory("PBKDF2WithHmacSHA256")`은 비밀번호를 `char[]`로 받아 전처리한 UTF-8 바이트를 그대로 넣을 수 없어 쓰지 않는다. |
| HKDF-SHA256 | JDK에 HKDF API가 없다. `Mac.getInstance("HmacSHA256")`로 RFC 5869 Extract·Expand를 직접 구현한다. 외부 라이브러리는 추가하지 않는다. |
| AES-256-GCM | `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec(128, iv)`, AAD는 `updateAAD()` |
| 비밀번호 전처리 | `java.text.Normalizer.normalize(input, Normalizer.Form.NFC)` 뒤 명세의 공백 6종만 양끝에서 잘라 UTF-8로 인코딩한다. 짝 없는 서로게이트가 있으면 거부한다. |
| 난수 | `java.security.SecureRandom` (IV·salt·비밀번호 생성) |

구현은 `:core:data`에 두고, `:core:domain`에는 포트 인터페이스만 둔다([ADR-AND-001](adr/ADR-AND-001-android-architecture.md)). 모든 구현은 [`docs/crypto-test-vectors.json`](../../../docs/crypto-test-vectors.json)으로 단위 테스트한다.

교차 검증(VLT-001): 공용 벡터의 암호문은 웹 구현으로 만들고 iOS도 같은 벡터를 통과한다. Android는 이 암호문을 `AesGcmVaultCipher`로 복호화하는 데서 그치지 않고, `JsonVaultSerializer`로 평문 볼트(vault-spec 4장)를 읽어 항목 값까지 확인한다. 같은 암호문을 vault-spec 6장 백업 파일로 감싸 `BackupUseCase.open`에 마스터 비밀번호만 넣어 여는 경로도 확인한다. 정책 경계값은 `Scripts/check-policy-acceptance.sh`의 Android 규칙이 테스트(`*Test.kt`)와 소스의 표준 문구를 함께 검사한다.

## 서버 통신

| 규격 | 구현 |
| :--- | :--- |
| HTTP(api-spec.md) | `HttpURLConnection`과 `org.json`(`core/data/.../remote/HttpApiClient`). 외부 HTTP 라이브러리는 추가하지 않는다. 엔드포인트는 `ApiEndpoints`, 오류 코드는 `ServerErrorCode`에 두고 `Scripts/check-api-contract.sh`가 명세와 대조한다. |
| 전송 보안(SEC-002) | 원격 서버는 HTTPS만 허용한다. 앱의 `network_security_config.xml`도 `localhost`·`127.0.0.1`만 HTTP를 허용한다. 에뮬레이터에서 로컬 백엔드에 붙을 때는 `adb reverse tcp:8080 tcp:8080` 뒤 `http://localhost:8080`을 쓴다. |
| 제한 시간 | 연결·읽기 30초(VLT-001) |
| 서버 주소 | `:app`의 `BuildConfig.SERVER_URL`. 기본은 `http://localhost:8080`(adb reverse로 잇는 로컬 백엔드)이고, 다른 서버는 `./gradlew assembleDebug -Pkeymanage.serverUrl=https://...`로 넘긴다. |
| 409 자동 병합 | `:core:domain`의 `SaveVaultUseCase`. 서버 볼트를 받아 병합한 뒤 서버 revision으로 다시 저장하고, 재시도는 3회까지다. 서버 revision이 마지막으로 본 값보다 낮으면 멈춘다. |

## 기기 저장과 동기화 대기

| 규격 | 구현 |
| :--- | :--- |
| 기기 기록(VLT-003) | `:core:domain`의 `DeviceRecord`(이메일, KDF 반복 횟수·salt, 암호화된 볼트, 기준 revision, 마지막으로 본 revision, 대기 표시)를 `DeviceRecordStore` 포트로 저장한다. 구현은 `:core:data`의 `FileDeviceRecordStore`이고, JSON 파일 하나를 임시 파일에 쓴 뒤 이름을 바꿔 원자적으로 교체한다. 앱은 `noBackupFilesDir`에 둔다(백업 제외 규칙과 함께 기기 밖으로 나가지 않는다). |
| 기기 기록 암호화 | 볼트는 이미 마스터 키 유도 키로 AES-GCM 암호화한 페이로드라 Keystore로 한 번 더 감싸지 않는다. 생체 해제용 키 보관(SEC-003)은 아래 "생체 해제" 절을 따른다. |
| 동기화 대기(VLT-002) | `:core:domain`의 `VaultSyncCoordinator`. 네트워크 오류·응답 형식 오류·5xx·429는 대기로 두고 성공 처리한다. 분명한 거절(용량 초과·읽기 전용·되돌림)은 오류로 올리고 자동 재전송을 멈춘다. 재시도 3회를 모두 충돌하면 대기 내용을 남긴 채 오류를 올린다. 30초 간격·재연결·잠금 해제 재전송의 시점은 화면 단계에서 이 조정자를 부른다. |

## 생체 해제

| 규격 | 구현 |
| :--- | :--- |
| 판정(SEC-003 규칙 4·8, 14일 재요구) | `:core:domain`의 `BiometricUnlock`. 켜짐 여부·마지막 마스터 비밀번호 해제 시각은 `BiometricSettingStore`, 감싼 키는 `BiometricKeyStore` 포트로 다룬다. 14일이 지나면 키를 남긴 채 비밀번호를 요구하고, 키가 무효가 되면 지우고 끈다. |
| 키 보관 | [ADR-AND-002](adr/ADR-AND-002-biometric-unlock-keystore.md). `:core:data`의 `KeystoreBiometricKeyStore`가 Keystore AES-256-GCM 키(매번 강한 생체 인증, 생체 등록 시 무효)로 볼트 암호화 키를 감싸 `noBackupFilesDir`에 둔다. 인증 화면은 `BiometricAuthenticator` 포트로 받아 `:app`에서 `BiometricPrompt`로 띄운다. |
| 잠금 해제 | `VaultSessionUseCase.unlockWithKey`. 기기 볼트만 복호화하고 서버 재로그인은 하지 않는다. 메모리 세션이 있으면 서버 볼트를 받는다. |
| 화면 | 잠금 화면은 `BiometricUnlocker`가 쓸 수 있다고 할 때만 "생체 인증으로 잠금 해제" 버튼을 보인다. 설정 탭 "보안"에 스위치를 두고 기기에 강한 생체 인증과 화면 잠금이 있을 때만 보인다. 인증 창은 `:app`의 `BiometricPrompt`(`BIOMETRIC_STRONG`, 취소 버튼 "마스터 비밀번호 사용")다. |
| 문구 | 14일 경과 "보안을 위해 14일마다 마스터 비밀번호를 입력해야 합니다.", 무효화 "생체 정보가 바뀌어 생체 인증을 껐습니다. 마스터 비밀번호로 잠금을 풀어 주세요.", 쓸 수 없음 "지금은 생체 인증을 쓸 수 없습니다. 마스터 비밀번호를 입력해 주세요." SEC-003 문구 표에는 iOS의 Face ID 버튼만 있어 Android 문구는 여기에 둔다. |
| 가입 직후 안내 | `AuthResult.isNewAccount`가 참이고 기기에서 쓸 수 있으면 Root가 볼트를 연 뒤 "생체 인증으로 잠금을 풀까요?" 대화상자(켜기·나중에)를 띄운다(SEC-003 값 표). 켜다 취소하면 조용히 닫고, 실패하면 설정에서 다시 하라고 안내한다. |
| 끄는 시점 | 로그아웃·계정 삭제·마스터 비밀번호 변경 뒤 `:app`이 끈다. 마스터 비밀번호 해제·로그인 때 14일 기준 시각을 새로 남긴다. |

## 암호화 백업

| 규격 | 구현 |
| :--- | :--- |
| 백업 파일(vault-spec 6장, VLT-004) | `:core:domain`의 `BackupFile`과 `BackupCodec` 포트. 구현은 `:core:data`의 `JsonBackupCodec`(org.json)이고, 파일 전체 5MB + 64KB, `vault` 필드 압축 JSON 5MB 상한과 `format`·`formatVersion`·`kdf.name` 검사를 맡는다. |
| 내보내기·복원 | `:core:domain`의 `BackupUseCase`. 내보내기는 지금 암호화 키와 계정 KDF 값을 쓰고, 복원은 파일의 KDF 값으로 키를 새로 만든다. 항목 되돌리기는 `VaultRestorer`이며 웹 `restoreItems`와 같은 순서·시각 규칙을 쓴다. 복원 결과 저장은 `VaultSyncCoordinator.save`로 넘긴다. |
| 파일 이름 | `keymanage-backup-YYYYMMDD.json`(기기 현지 날짜) |
| 화면 | 설정 탭의 백업 화면. 파일 위치는 저장소 접근 프레임워크(`CreateDocument`·`OpenDocument`)로 고르고, `:app`의 `ContentResolverDocuments`가 파일 상한(5MB + 64KB)보다 1바이트 더까지만 읽는다. 내보내기·복원은 키를 쥔 Root가 `BackupSession`으로 하고, 복원 결과는 일반 수정처럼 저장한다. 복원 전 안내 대화상자를 거치고, 복원 중에는 다시 실행하지 않는다. |

## 인증 화면

| 규격 | 구현 |
| :--- | :--- |
| 가입·로그인(SEC-002) | `:core:domain`의 `AuthUseCase`. 웹 `authService`와 같이 이메일을 정규화·형식 검사한 뒤에만 키를 유도하고, prelogin KDF 값이 규격 밖이면 로그인 요청을 보내지 않는다. 새 마스터 비밀번호는 코드포인트 12자 이상과 확인 일치를 검사한다. |
| 화면 흐름 | `:feature:auth` RIB(로그인·가입 두 모드). Root가 처음에 붙이고, 인증이 끝나면 뗀다. 키 유도는 `Dispatchers.Default`에서 돈다. 문구는 SEC-002·COM-002 표 그대로다(`AuthMessages`). |
| 볼트 열기·잠금 해제(SEC-003) | `:core:domain`의 `VaultSessionUseCase`. 인증 직후 볼트가 없으면 빈 볼트를 저장하고, 대기 내용은 병합해 올린다. 잠금 해제는 기기 기록 복호화로 확인하고, 세션이 없으면 인증 해시로 다시 로그인하며 서버에 닿지 못하면 세션 없이 연다. KDF 값이 다르거나 인증 실패면 로그인 화면으로 보낸다. |
| 화면 흐름 | Root가 기기 기록이 있으면 `:feature:unlock`, 없으면 `:feature:auth`를 붙이고, 열리면 `:feature:vaultlist`를 붙인다. 화면을 바꿀 때 이전 RIB을 먼저 떼므로 잠그면 목록 RIB과 평문 상태가 함께 사라진다. 암호화 키는 Root만 들고 잠글 때 0으로 덮어쓴다. 잠금이 풀리면 `:feature:main`(볼트·생성기·설정 탭)을 붙인다. ADR-AND-001의 LoggedIn 역할(키 보관·잠금)은 Root가 맡는다. |
| 항목 상세·추가·수정·삭제(VLT-001) | `:core:domain`의 `VaultEditing`(웹 `addItem`·`updateItem`·`deleteItem`과 같은 규칙, 바뀐 것이 없으면 저장하지 않음). 화면은 Apple처럼 `:feature:vaultlist` 안에 둔다. 저장은 키를 쥔 Root가 `VaultSessionUseCase.save`로 하고, 세션 없이 연 볼트는 대기로 둔다. |
| 클립보드 자동 비우기(COM-001) | `:core:domain`의 `ClipboardBurner`(30초, 다시 복사하면 새로 잼, 밖에서 복사하면 타이머 무효, 잠그면 바로 비움). `:core:data`의 `AndroidClipboard`가 `EXTRA_IS_SENSITIVE`로 표시해 Android 13+ 복사 미리 보기에서 가리고, 다른 앱의 복사는 `OnPrimaryClipChangedListener`로 알아챈다. |
| 계정 삭제·비밀번호 변경(SEC-004, SEC-005) | `:core:domain`의 `AccountUseCase`. 입력한 비밀번호로 만든 키가 지금 키와 같은지 기기에서 먼저 확인해, 틀린 비밀번호로 서버 시도 횟수를 쓰지 않는다. 비밀번호 변경은 새 salt·현재 반복 횟수로 새 키를 만들고 마지막 저장 볼트(대기 중 변경 포함)를 다시 암호화해 한 번에 교체하며, 성공하면 기기 기록을 새 KDF 값·새 키로 바꾸고 Root가 새 키·새 세션으로 바꿔 든다. 계정 삭제가 끝나면 기기 기록을 지우고 로그인 화면에 완료 안내를 보이며 자동 잠금 설정은 남긴다. 둘 다 세션이 있을 때만 한다. |
| 자동 잠금(SEC-003 규칙 2) | `:core:domain`의 `AutoLock`(웹 `createAutoLock`과 같은 규칙, 선택지 즉시·1분·5분·15분·1시간, 기본 5분). Root의 `RootAutoLock`이 잠금이 풀린 동안 돌리고, `MainActivity`의 `onStart`·`onStop`·`onUserInteraction`을 받는다. 경과 시간은 `SystemClock.elapsedRealtime`으로 재 기기 시계 변경에 흔들리지 않는다. 설정은 `SharedPreferences`(`PreferencesAutoLockStore`)에 두고 계정과 묶지 않는다. |
| 대기 중 로그아웃 확인(VLT-002 규칙 11) | 설정 탭이 `VaultSessionUseCase.hasPendingChanges`로 확인하고 대기 내용이 있으면 확인 대화상자를 띄운다. |
| 앱 전환 화면 가림(SEC-003 규칙 3) | `MainActivity`의 `FLAG_SECURE`. 화면 녹화·스크린숏도 막힌다. |
| 가입 강도 게이지(SEC-002 규칙 8) | `AuthViewState.passwordScore`(가입 모드, 비밀번호가 있을 때)와 `KMGaugeBar`. 60점 미만이면 SEC-002 취약 문구를 보이되 가입은 막지 않는다. |
| 비밀번호 생성·강도(GEN-001) | `:core:domain`의 `PasswordGenerator`(`SecureRandom.nextInt`로 균등 추출, 빠진 문자군이 있으면 통째로 다시 뽑음)와 `PasswordStrength`(점수·취약·이어지는 숫자). 웹 `passwordGenerator`·`passwordStrength`와 같은 규칙이다. 항목 상세·입력에 게이지와 안내를, 입력 폼에 기본 옵션 생성 버튼을 둔다. 옵션을 고르는 생성기 화면은 탭 구조와 함께 붙인다. |


| 규격 | 구현 |
| :--- | :--- |
| 평문 볼트(vault-spec 4장) 읽기·쓰기 | Android 플랫폼의 `org.json`. 외부 직렬화 라이브러리는 추가하지 않는다. 모르는 필드는 원래 JSON 조각으로 보관했다가 그대로 다시 쓴다. |
| 시각 | `java.time.OffsetDateTime`으로 시간대 표기가 있는 ISO 8601만 받고, 쓸 때는 `yyyy-MM-dd'T'HH:mm:ss'Z'`(UTC) |
| 병합·삭제 표시·검증 | `:core:domain`의 `VaultMerger`·`VaultRules`(순수 Kotlin). 웹 `mergeVaults`와 같은 순서로 병합한다. |

## 색상 토큰

값은 `node Tools/design-tokens/generate.mjs`가 만드는 `core/designsystem/.../generated/DesignTokens.kt`에서 온다. `KeyManageTheme`이 시멘틱 토큰을 Material3 `ColorScheme`에 아래처럼 넣으므로 Material 컴포넌트도 토큰 색을 쓴다. 슬롯이 없는 토큰(`accent`, `ring` 등)은 `KeyManageThemeValues.colors.<이름>`으로 쓴다. 다이내믹 컬러는 쓰지 않고, 기본 모드는 다크다.

| 토큰 | ColorScheme 슬롯 |
| :--- | :--- |
| `background` / `foreground` | `background` / `onBackground` |
| `card` / `card-foreground` | `surface` / `onSurface` |
| `muted` / `muted-foreground` | `surfaceVariant`·`surfaceContainerHighest` / `onSurfaceVariant` |
| `primary` / `primary-foreground` | `primary` / `onPrimary` |
| `accent` / `accent-foreground` | `secondary` / `onSecondary` |
| `destructive` / `destructive-foreground` | `error` / `onError` |
| `border` / `input` | `outline` / `outlineVariant` |
| `overlay` | `scrim` |

## 글자 역할

크기는 웹 rem 값에 16을 곱한 sp다(예: `display` 1.5rem → 24sp). sp라서 Android 글꼴 배율을 따른다. 역할은 Material3 `Typography` 슬롯에 넣는다.

| 역할 | Typography 슬롯 |
| :--- | :--- |
| `display` | `displaySmall`, `headlineSmall` |
| `title` | `titleLarge` |
| `heading` | `titleMedium` |
| `body` | `bodyLarge` |
| `label` | `bodyMedium`, `labelLarge` |
| `caption` | `bodySmall`, `labelSmall` |

## 글꼴

| 용도 | 글꼴 |
| :--- | :--- |
| UI 레이블·헤더 | `Roboto` (시스템) |
| 비밀번호 | `FontFamily.Monospace` |

## 아이콘

아이콘은 Material Icons를 쓴다.

| 용도 | 아이콘 |
| :--- | :--- |
| 비밀번호 보기 | `Icons.Default.Visibility` |
| 비밀번호 숨김 | `Icons.Default.VisibilityOff` |
| 클립보드 복사 | `Icons.Default.ContentCopy` |
| 복사 완료 | `Icons.Default.CheckCircle` |
| 암호 재생성 | `Icons.Default.Refresh` |
| 볼트 잠금 | `Icons.Default.Lock` |
| 경고·취약 | `Icons.Default.Warning` |
| 오프라인 배너 | `Icons.Default.WifiOff` |
