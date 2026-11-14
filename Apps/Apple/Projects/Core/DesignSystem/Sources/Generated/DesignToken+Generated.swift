// 생성 파일입니다. 직접 고치지 말고 Tools/design-tokens/tokens.json을 고친 뒤 node Tools/design-tokens/generate.mjs를 실행하세요 (ADR-0026).
import CoreGraphics

extension DesignToken {
    public enum Colors {
        public static let background = ColorToken(lightHex: "#FFFFFF", darkHex: "#000000")
        public static let foreground = ColorToken(lightHex: "#0A0A0A", darkHex: "#FAFAFA")
        public static let card = ColorToken(lightHex: "#FFFFFF", darkHex: "#141414")
        public static let cardForeground = ColorToken(lightHex: "#0A0A0A", darkHex: "#FAFAFA")
        public static let primary = ColorToken(lightHex: "#0A0A0A", darkHex: "#FAFAFA")
        public static let primaryForeground = ColorToken(lightHex: "#FFFFFF", darkHex: "#0A0A0A")
        public static let accent = ColorToken(lightHex: "#059669", darkHex: "#10B981")
        public static let accentForeground = ColorToken(lightHex: "#FFFFFF", darkHex: "#0A0A0A")
        public static let muted = ColorToken(lightHex: "#F5F5F5", darkHex: "#262626")
        public static let mutedForeground = ColorToken(lightHex: "#737373", darkHex: "#A3A3A3")
        public static let destructive = ColorToken(lightHex: "#DC2626", darkHex: "#EF4444")
        public static let destructiveForeground = ColorToken(lightHex: "#FFFFFF", darkHex: "#FFFFFF")
        public static let border = ColorToken(lightHex: "#E5E5E5", darkHex: "rgba(255, 255, 255, 0.12)")
        public static let input = ColorToken(lightHex: "#E5E5E5", darkHex: "#262626")
        public static let ring = ColorToken(lightHex: "#0A0A0A", darkHex: "#FAFAFA")
    }

    public enum Spacing {
        public static let space1: CGFloat = 4
        public static let space2: CGFloat = 8
        public static let space3: CGFloat = 12
        public static let space4: CGFloat = 16
        public static let space5: CGFloat = 20
        public static let space6: CGFloat = 24
    }

    public enum Radius {
        public static let sm: CGFloat = 6
        public static let md: CGFloat = 8
        public static let lg: CGFloat = 12
    }
}
