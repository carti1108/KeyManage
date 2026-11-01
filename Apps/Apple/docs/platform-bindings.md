# Apple 플랫폼 바인딩

- 대상 명세: [vault-spec.md](../../../docs/vault-spec.md), [design-system.md](../../../docs/design-system.md)
- 대상 코드: `Apps/Apple/Projects/`

> **요약** iOS·macOS 클라이언트가 공통 암호 규격과 디자인 시스템을 어떤 프레임워크로 구현하는지 적는다.

## 암호 라이브러리

| 규격 | 구현 |
| :--- | :--- |
| PBKDF2 | `CommonCrypto` (`CCKeyDerivationPBKDF`) |
| AES-256-GCM | `CryptoKit.AES.GCM` |

## 색상 토큰

iOS 구현이 SwiftUI든 UIKit이든 모두 지원하도록 `DesignToken` 정적 열거형 구조를 유지한다.

```swift
public enum DesignToken {
    // UIColor (UIKit 지원)
    public enum UI {
        public static let background = UIColor(named: "ColorBackground") ?? UIColor(hex: "#0F172A")
        public static let card = UIColor(named: "ColorCard") ?? UIColor(hex: "#1E293B")
        public static let primary = UIColor(named: "ColorPrimary") ?? UIColor(hex: "#38BDF8")
        public static let accent = UIColor(named: "ColorAccent") ?? UIColor(hex: "#10B981")
        public static let destructive = UIColor(named: "ColorDestructive") ?? UIColor(hex: "#EF4444")
    }
    
    // SwiftUI Color (SwiftUI 지원)
    public enum View {
        public static let background = Color(uiColor: UI.background)
        public static let card = Color(uiColor: UI.card)
        public static let primary = Color(uiColor: UI.primary)
        public static let accent = Color(uiColor: UI.accent)
        public static let destructive = Color(uiColor: UI.destructive)
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
