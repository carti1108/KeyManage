# Android 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: `Apps/Shared`(공유 Kotlin 모듈과 그 Android 소스), `Apps/Android`(Compose 뷰, 디자인 시스템, 앱 진입점)

> **요약** Android 앱이 쓰는 라이브러리와 이름을 적는다. 도메인과 RIB은 공유 Kotlin 모듈에 있고([ADR-AND-003](adr/ADR-AND-003-shared-kotlin-ribs.md)), 이 문서는 그중 Android에서 달라지는 부분과 화면 쪽 이름을 다룬다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2·HKDF-SHA256·AES-256-GCM | 공유 `crypto` 모듈의 `VaultCrypto`. `cryptography-kotlin`(`cryptography-core`, `cryptography-provider-optimal`)을 쓰고 Android에서는 JDK 제공자가 돈다. |
| 키 유도 스레드 | PBKDF2는 `Dispatchers.Default`에서 돈다(`domain`의 `deriveKeys`). 화면 스레드를 막지 않는다. |
| 비밀번호 전처리 | `crypto`의 `PasswordPreprocessor`. NFC 정규화만 플랫폼별이고 Android는 `java.text.Normalizer`다(`Nfc.android.kt`). |
| 메모리의 키 | 잠그거나 세션을 지울 때 볼트 암호화 키 바이트를 0으로 덮는다(`EncryptionKey.wipe`). |

모든 구현은 [`docs/crypto-test-vectors.json`](../../../docs/crypto-test-vectors.json)으로 공유 모듈에서 단위 테스트한다. 다른 플랫폼과 같은 암호문 벡터를 여는 교차 검증(VLT-001)도 공유 테스트에 있다. 정책 경계값은 `Scripts/check-policy-acceptance.sh`의 Android 규칙이 공유 Kotlin 테스트와 소스의 표준 문구를 함께 검사한다.

## 서버 통신

| 규격 | 구현 |
| :--- | :--- |
| HTTP(api-spec.md) | 공유 `server-http` 모듈의 Ktor 클라이언트(ADR-0028). Android 엔진은 OkHttp다. 오류 코드는 `domain`의 `ServerErrorCode`에 두고 `Scripts/check-api-contract.sh`가 명세와 대조한다. |
| 전송 보안(SEC-002) | 원격 서버는 HTTPS만 허용한다. 앱의 `network_security_config.xml`도 `localhost`·`127.0.0.1`만 HTTP를 허용한다. 에뮬레이터에서 로컬 백엔드에 붙을 때는 `adb reverse tcp:8080 tcp:8080` 뒤 `http://localhost:8080`을 쓴다. |
| 제한 시간 | 요청 30초(VLT-001) |
| 서버 주소 | `:app`의 `BuildConfig.SERVER_URL`. 기본은 `http://localhost:8080`(adb reverse로 잇는 로컬 백엔드)이고, 다른 서버는 `./gradlew assembleDebug -Pkeymanage.serverUrl=https://...`로 넘긴다. |
| 409 자동 병합·동기화 대기(VLT-001, VLT-002) | `domain`의 `VaultSync`와 `mergeVaults`. 규칙은 웹과 같은 코드다. |

## 기기 저장

| 규격 | 구현 |
| :--- | :--- |
| 기기 기록(VLT-003) | `domain`의 `DeviceRecord`와 `DeviceRecordStore` 포트. Android 구현은 `device-store`의 `FileDeviceRecordStore`이고 `noBackupFilesDir`에 둔다. |
| 기기 기록 암호화 | 볼트는 이미 마스터 키 유도 키로 AES-GCM 암호화한 페이로드라 Keystore로 한 번 더 감싸지 않는다. |
| 설정 값 | 자동 잠금 시간과 생체 해제 설정은 `android-session`의 `PreferencesAutoLockSettingStore`·`PreferencesBiometricSettingStore`(SharedPreferences `keymanage.settings`). |
| 평문 볼트(vault-spec 4장) 읽기·쓰기 | `kotlinx.serialization.json`. 규칙과 병합은 `domain`의 `vault` 패키지에 있다. |

## 생체 해제

| 규격 | 구현 |
| :--- | :--- |
| 판정(SEC-003 규칙 4·8, 14일 재요구) | `domain`의 `BiometricUnlock`. 켜짐 여부·마지막 마스터 비밀번호 해제 시각·비밀번호 요구 표시는 `BiometricSettingStore`, 감싼 키는 `BiometricKeyStore` 포트로 다룬다. |
| 키 보관 | [ADR-AND-002](adr/ADR-AND-002-biometric-unlock-keystore.md). `android-session`의 `KeystoreBiometricKeyStore`가 `secure-store`의 Keystore 생체 전용 키(매번 강한 생체 인증, 생체 등록 시 무효)로 볼트 암호화 키를 감싼다. 인증 창은 `AndroidBiometricAuthenticator`(BiometricPrompt)다. |
| 잠금 해제 | `VaultSession.unlockWithBiometrics`. 기기 볼트만 복호화하고 서버 재로그인은 하지 않는다. 세션이 없으면 볼트 화면의 다시 연결 안내에서 마스터 비밀번호를 받는다. |
| 화면 | 잠금 화면은 쓸 수 있을 때만 생체 해제 버튼을 보인다. 설정 탭의 스위치는 기기에 강한 생체 인증과 화면 잠금이 있을 때만 보인다. 문구는 공유 `BiometricUnlockMessages`다. |
| 가입 직후 안내 | 가입으로 볼트를 열었고 기기에서 쓸 수 있으며 아직 켜지 않았으면 Main RIB이 "생체 인증으로 잠금을 풀까요?"를 띄운다(SEC-003 값 표). |
| 끄는 시점 | 로그아웃·계정 삭제·마스터 비밀번호 변경·계정 전환 때 세션이 끈다. 세션이 만료되면 다음 해제는 마스터 비밀번호를 요구한다. |

## 암호화 백업

| 규격 | 구현 |
| :--- | :--- |
| 백업 파일(vault-spec 6장, VLT-004) | `domain`의 `backup` 패키지(`createBackupFile`, `readBackupFile`, `openBackup`, `restoreItems`). 크기 상한은 `MAX_BACKUP_FILE_BYTES`다. |
| 파일 이름 | `keymanage-backup-YYYYMMDD.json`(기기 현지 날짜) |
| 파일 고르기 | 저장소 접근 프레임워크(`CreateDocument`·`OpenDocument`). `android-session`의 `AndroidBackupFiles`가 맡는다. |

## 화면과 수명주기

| 규격 | 구현 |
| :--- | :--- |
| 화면 흐름 | 공유 Root RIB이 기기 기록이 있으면 Unlock, 없으면 Auth를 붙이고, 열리면 Main(VaultList·PasswordGenerator·Settings)을 붙인다. 잠그면 Main 트리를 떼어 평문 상태가 함께 사라진다. |
| 뷰 | 피처마다 `:feature:<이름>:ui`의 `Compose<이름>View`가 공유 ViewContract를 구현한다. ViewState를 받아 그리고 ViewAction만 보낸다. |
| 앱 조립 | `android-session`의 `AndroidKeyManageApp`이 세션과 플랫폼 구현을 조립한다. `:app`의 `KeyManageProcess`가 프로세스에 하나를 두고, `MainActivity`는 화면을 붙이고 수명주기(`onStart`·`onStop`·사용자 입력)를 넘긴다. |
| 클립보드 자동 비우기(COM-001) | `domain`의 `ClipboardBurner`(30초). `android-session`의 `AndroidClipboardWriter`가 쓰고 비운다. Android는 변경 알림을 쓰기가 끝난 뒤 여러 번 보내므로, 내가 쓴 직후 0.5초 안의 알림은 내 것으로 본다. |
| 자동 잠금(SEC-003 규칙 2) | `domain`의 `AutoLock`. 선택지 즉시·1분·5분·15분·1시간, 기본 5분. |
| 앱 전환 화면 가림(SEC-003 규칙 3) | `MainActivity`의 `FLAG_SECURE`. 화면 녹화·스크린숏도 막힌다. |
| 비밀번호 생성·강도(GEN-001, SEC-002 규칙 8) | `domain`의 `generatePassword`·`scorePassword`. 가입 화면 게이지는 `KMGaugeBar`다. |
| 화면만 확인 | 피처마다 `:feature:<이름>:example` 앱이 공유 Testing 시나리오로 그 피처 화면만 띄운다. |

## 색상 토큰

값은 `node Tools/design-tokens/generate.mjs`가 만드는 `core/designsystem/.../generated/DesignTokens.kt`에서 온다. `KeyManageTheme`이 시멘틱 토큰을 Material3 `ColorScheme`에 아래처럼 넣으므로 Material 컴포넌트도 토큰 색을 쓴다. 슬롯이 없는 토큰(`accent`, `ring` 등)은 `KeyManageThemeValues.colors.<이름>`으로 쓴다. 다이내믹 컬러는 쓰지 않고, 기본 모드는 다크다.

| 토큰 | ColorScheme 슬롯 |
| :--- | :--- |
| `background` / `foreground` | `background` / `onBackground` |
| `card` / `card-foreground` | `surface`·`surfaceBright`·`surfaceContainerLow`·`surfaceContainer`·`surfaceContainerHigh` / `onSurface` |
| `muted` / `muted-foreground` | `surfaceVariant`·`surfaceContainerHighest`·`primaryContainer`·`secondaryContainer`·`tertiaryContainer` / `onSurfaceVariant` |
| `background` / `foreground` (보조 자리) | `surfaceDim`·`surfaceContainerLowest`·`inverseOnSurface` / `surfaceTint`·`inverseSurface`·`on*Container` |
| `primary` / `primary-foreground` | `primary` / `onPrimary`·`inversePrimary` |
| `accent` / `accent-foreground` | `secondary`·`tertiary` / `onSecondary`·`onTertiary` |
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

## 화면 모양

목업(`Tools/figma-builder/code.js`의 Android 줄)은 어떤 정보를 어느 화면에 어떤 순서로 두는지의 기준이다(design-system.md 5.1 "화면 정보 구조", 5.2). 모양과 컨트롤은 목업의 그림이 아니라 Material 3를 따른다(design-system.md 5장). 목업의 Android 줄도 M3 규격(Filled 입력 칸, `TopAppBar`, `NavigationBar`, `AlertDialog`)으로 그리고, 아이콘은 `KMIcons`와 같은 Material Icons 경로를 쓴다. 그래도 목업은 도형으로 그린 그림이므로 둘이 다르면 M3 컴포넌트의 실제 모양이 우선이다.

| 요소 | 구현 |
| :--- | :--- |
| 화면 틀 | `KMScreen`. M3 `TopAppBar`(`KMTopBar`)와 본문(좌우 `space-4`, 간격 `space-4`)을 묶는다. 뒤로 가기는 M3 표준 화살표다. 탭 첫 화면(볼트·생성기·설정)은 뒤로 가기 없이 제목만 둔다. |
| 입력 칸 | `KMTextField`·`KMPasswordField`. M3 Filled `TextField`이고 이름표는 M3 방식대로 칸 안에서 위로 떠오른다. 비밀번호 칸의 보기·복사는 아이콘 버튼이다. |
| 버튼 | `KMButton` 기본 크기(높이 40, M3 기본 모양). |
| 대화상자 | M3 `AlertDialog`. 계정 삭제도 설정 화면 위에 대화상자로 띄운다(design-system.md 6.9). |
| 아래 탭 | M3 `NavigationBar` 기본 모양에 탭마다 아이콘을 둔다. |
| 볼트 목록 | 줄은 `KMCard`다. 원 안의 첫 글자, 이름·아이디, 오른쪽 복사 아이콘을 둔다. 복사는 상세를 열지 않고 그 항목의 비밀번호를 복사하며 COM-001 타이머를 시작한다. 추가 버튼은 M3 FAB에 `primary` 색을 입힌다. |
| 항목 상세·설정 | 목업대로 이름표 왼쪽·값 오른쪽의 줄(위아래 `space-3`)로 그린다. |
| 간격·모서리·글자 | 화면 코드는 토큰과 M3 기본값만 쓴다. 목업의 숫자를 직접 옮기지 않고, 글자는 위 글자 역할 6개를 굵기까지 그대로 쓴다. |
| 목업에 없는 요소 | 기능에 닿는 길이라 남긴다: 볼트 목록 "잠금", 상세 "수정"·비밀번호 복사, 설정의 생체 인증 스위치, 안내 띠, 되돌림 경고. |

## 아이콘

모양은 Material Icons를 쓴다(design-system.md 7장). 아이콘 라이브러리 의존성은 두지 않고, 필요한 것만 `KMIcons`에 Material Icons의 24dp 경로 데이터로 둔다(design-system.md 1.2 컴포넌트 소유).

| 용도 | 아이콘 | `KMIcons` |
| :--- | :--- | :--- |
| 뒤로 가기 | `arrow_back` | `Back` |
| 볼트 잠금, 볼트 탭 | `lock` | `Lock` |
| 비밀번호 보기 | `visibility` | `Visibility` |
| 비밀번호 숨김 | `visibility_off` | `VisibilityOff` |
| 클립보드 복사 | `content_copy` | `Copy` |
| 생성기 탭 | `vpn_key` | `Key` |
| 설정 탭 | `settings` | `Settings` |

아래는 아직 `KMIcons`에 없는 용도다. 넣을 때 쓸 Material Icons 이름이다.

| 용도 | 아이콘 |
| :--- | :--- |
| 복사 완료 | `check_circle` |
| 암호 재생성 | `refresh` |
| 경고·취약 | `warning` |
| 오프라인 배너 | `wifi_off` |
