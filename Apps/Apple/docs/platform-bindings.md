# Apple 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: `Apps/Apple/Projects/`(화면·디자인 시스템), `Apps/Shared`(암호·도메인, Kotlin)

> **요약** iOS·macOS 클라이언트가 공통 암호 규격과 디자인 시스템을 어떤 프레임워크로 구현하는지 적는다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2·HKDF·AES-256-GCM | 공유 Kotlin `:crypto`(cryptography-kotlin `provider-optimal`, Apple 대상은 CommonCrypto·CryptoKit 제공자) |

## 색상 토큰

iOS·macOS 구현이 SwiftUI든 UIKit/AppKit이든 모두 지원하도록 `KeyManageDesignSystem` 모듈 내 `DesignToken` 정적 열거형 구조를 둔다. 시스템 다크/라이트 모드 전환에 동적으로 반응한다.

```swift
public enum DesignToken {
    // UIColor / NSColor (UIKit / AppKit 지원, @MainActor)
    public enum UI {
        public static var background: PlatformColor { Colors.background.dynamicPlatformColor() }
        public static var card: PlatformColor { Colors.card.dynamicPlatformColor() }
        public static var primary: PlatformColor { Colors.primary.dynamicPlatformColor() }
        public static var accent: PlatformColor { Colors.accent.dynamicPlatformColor() }
        public static var destructive: PlatformColor { Colors.destructive.dynamicPlatformColor() }
    }
    
    // SwiftUI Color (SwiftUI 지원, 동적 전환)
    public enum View {
        public static var background: Color { Colors.background.color() }
        public static var card: Color { Colors.card.color() }
        public static var primary: Color { Colors.primary.color() }
        public static var accent: Color { Colors.accent.color() }
        public static var destructive: Color { Colors.destructive.color() }
    }
}
```

## 글꼴

| 용도 | 글꼴 |
| :--- | :--- |
| UI 레이블·헤더 | `SF Pro` (시스템) |
| 비밀번호 (SwiftUI) | `Font.system(.body, design: .monospaced)` |
| 비밀번호 (UIKit) | `UIFont.monospacedSystemFont(ofSize: 16, weight: .medium)` |

## 아이콘

아이콘은 SF Symbols를 쓴다(UIKit·SwiftUI 공용).

| 용도 | 아이콘 |
| :--- | :--- |
| 비밀번호 보기 | `eye` |
| 비밀번호 숨김 | `eye.slash` |
| 클립보드 복사 | `doc.on.doc` |
| 복사 완료 | `checkmark.circle.fill` |
| 암호 재생성 | `arrow.clockwise` |
| 볼트 잠금 | `lock.fill` |
| 경고·취약 | `exclamationmark.triangle.fill` |
| 오프라인 배너 | `wifi.slash` |
