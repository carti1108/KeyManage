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

## 서버 통신

| 규격 | 구현 |
| :--- | :--- |
| HTTP(api-spec.md) | `HttpURLConnection`과 `org.json`(`core/data/.../remote/HttpApiClient`). 외부 HTTP 라이브러리는 추가하지 않는다. 엔드포인트는 `ApiEndpoints`, 오류 코드는 `ServerErrorCode`에 두고 `Scripts/check-api-contract.sh`가 명세와 대조한다. |
| 전송 보안(SEC-002) | 원격 서버는 HTTPS만 허용한다. 앱의 `network_security_config.xml`도 `localhost`·`127.0.0.1`만 HTTP를 허용한다. 에뮬레이터에서 로컬 백엔드에 붙을 때는 `adb reverse tcp:8080 tcp:8080` 뒤 `http://localhost:8080`을 쓴다. |
| 제한 시간 | 연결·읽기 30초(VLT-001) |
| 409 자동 병합 | `:core:domain`의 `SaveVaultUseCase`. 서버 볼트를 받아 병합한 뒤 서버 revision으로 다시 저장하고, 재시도는 3회까지다. 서버 revision이 마지막으로 본 값보다 낮으면 멈춘다. |

## 기기 저장과 동기화 대기

| 규격 | 구현 |
| :--- | :--- |
| 기기 기록(VLT-003) | `:core:domain`의 `DeviceRecord`(이메일, KDF 반복 횟수·salt, 암호화된 볼트, 기준 revision, 마지막으로 본 revision, 대기 표시)를 `DeviceRecordStore` 포트로 저장한다. 구현은 `:core:data`의 `FileDeviceRecordStore`이고, JSON 파일 하나를 임시 파일에 쓴 뒤 이름을 바꿔 원자적으로 교체한다. 앱은 `noBackupFilesDir`에 둔다(백업 제외 규칙과 함께 기기 밖으로 나가지 않는다). |
| 기기 기록 암호화 | 볼트는 이미 마스터 키 유도 키로 AES-GCM 암호화한 페이로드라 Keystore로 한 번 더 감싸지 않는다. 생체 해제용 키 보관(SEC-003)은 화면 단계에서 Keystore로 따로 다룬다. |
| 동기화 대기(VLT-002) | `:core:domain`의 `VaultSyncCoordinator`. 네트워크 오류·응답 형식 오류·5xx·429는 대기로 두고 성공 처리한다. 분명한 거절(용량 초과·읽기 전용·되돌림)은 오류로 올리고 자동 재전송을 멈춘다. 재시도 3회를 모두 충돌하면 대기 내용을 남긴 채 오류를 올린다. 30초 간격·재연결·잠금 해제 재전송의 시점은 화면 단계에서 이 조정자를 부른다. |

## 암호화 백업

| 규격 | 구현 |
| :--- | :--- |
| 백업 파일(vault-spec 6장, VLT-004) | `:core:domain`의 `BackupFile`과 `BackupCodec` 포트. 구현은 `:core:data`의 `JsonBackupCodec`(org.json)이고, 파일 전체 5MB + 64KB, `vault` 필드 압축 JSON 5MB 상한과 `format`·`formatVersion`·`kdf.name` 검사를 맡는다. |
| 내보내기·복원 | `:core:domain`의 `BackupUseCase`. 내보내기는 지금 암호화 키와 계정 KDF 값을 쓰고, 복원은 파일의 KDF 값으로 키를 새로 만든다. 항목 되돌리기는 `VaultRestorer`이며 웹 `restoreItems`와 같은 순서·시각 규칙을 쓴다. 복원 결과 저장은 `VaultSyncCoordinator.save`로 넘긴다. |
| 파일 이름 | `keymanage-backup-YYYYMMDD.json`(기기 현지 날짜). 파일 선택은 화면 단계에서 저장소 접근 프레임워크(SAF)로 한다. |

## 볼트 JSON

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
