# Android 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: 없음 (Android 클라이언트는 아직 코드가 없다)

> **요약** Android 클라이언트를 만들 때 쓸 라이브러리와 이름을 미리 적어 둔다. 공통 명세에 있던 Android 항목을 옮긴 것이다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2 | `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` |
| HKDF-SHA256 | JDK에 HKDF API가 없다. `Mac.getInstance("HmacSHA256")`로 RFC 5869 Extract·Expand를 직접 구현한다. 외부 라이브러리는 추가하지 않는다. |
| AES-256-GCM | `Cipher.getInstance("AES/GCM/NoPadding")`, `GCMParameterSpec(128, iv)`, AAD는 `updateAAD()` |
| 비밀번호 전처리 | `java.text.Normalizer.normalize(input, Normalizer.Form.NFC)` 뒤 명세의 공백 6종만 양끝에서 잘라 UTF-8로 인코딩한다. 짝 없는 서로게이트가 있으면 거부한다. |
| 난수 | `java.security.SecureRandom` (IV·salt·비밀번호 생성) |

구현은 `:core:data`에 두고, `:core:domain`에는 포트 인터페이스만 둔다([ADR-AND-001](adr/ADR-AND-001-android-architecture.md)). 모든 구현은 [`docs/crypto-test-vectors.json`](../../../docs/crypto-test-vectors.json)으로 단위 테스트한다.

## 색상 토큰

Jetpack Compose의 Material3 `darkColorScheme()`와 `lightColorScheme()`에 공통 토큰 값을 그대로 넣는다. 화면에서는 `MaterialTheme.colorScheme.*`로 쓴다.

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
