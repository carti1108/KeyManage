# Android 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: 없음 (Android 클라이언트는 아직 만들지 않았다)

> **요약** Android 클라이언트를 만들 때 쓸 라이브러리와 이름을 미리 적어 둔다. 공통 명세에 있던 Android 항목을 옮긴 것이다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2 | `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` |
| AES-256-GCM | `Cipher.getInstance("AES/GCM/NoPadding")` |

## 색상 토큰

Material3 `darkColorScheme()`와 `lightColorScheme()`에 공통 토큰 값을 그대로 넣는다. 화면에서는 `MaterialTheme.colorScheme.*`로 쓴다.

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
