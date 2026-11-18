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
