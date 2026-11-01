import SwiftUI
#if canImport(UIKit)
import UIKit
public typealias PlatformColor = UIColor
public typealias PlatformFont = UIFont
#elseif canImport(AppKit)
import AppKit
public typealias PlatformColor = NSColor
public typealias PlatformFont = NSFont
#endif

public enum DesignToken {
    public enum Mode: Sendable {
        case light
        case dark
    }

    public struct ColorToken: Equatable, Sendable {
        public let lightHex: String
        public let darkHex: String

        public init(lightHex: String = "", darkHex: String = "") {
            self.lightHex = lightHex
            self.darkHex = darkHex
        }

        public func color(mode: Mode = .dark) -> Color {
            // RED STUB: minimal stub
            return .clear
        }

        public func platformColor(mode: Mode = .dark) -> PlatformColor {
            // RED STUB: minimal stub
            #if canImport(UIKit)
            return .clear
            #elseif canImport(AppKit)
            return .clear
            #endif
        }
    }

    public enum Colors {
        // RED STUB: minimal stub placeholder values
        public static let background = ColorToken()
        public static let foreground = ColorToken()
        public static let card = ColorToken()
        public static let cardForeground = ColorToken()
        public static let primary = ColorToken()
        public static let primaryForeground = ColorToken()
        public static let accent = ColorToken()
        public static let accentForeground = ColorToken()
        public static let muted = ColorToken()
        public static let mutedForeground = ColorToken()
        public static let destructive = ColorToken()
        public static let destructiveForeground = ColorToken()
        public static let border = ColorToken()
        public static let input = ColorToken()
        public static let ring = ColorToken()
    }

    public enum View {
        public static var background: Color { Colors.background.color() }
        public static var foreground: Color { Colors.foreground.color() }
        public static var card: Color { Colors.card.color() }
        public static var cardForeground: Color { Colors.cardForeground.color() }
        public static var primary: Color { Colors.primary.color() }
        public static var primaryForeground: Color { Colors.primaryForeground.color() }
        public static var accent: Color { Colors.accent.color() }
        public static var accentForeground: Color { Colors.accentForeground.color() }
        public static var muted: Color { Colors.muted.color() }
        public static var mutedForeground: Color { Colors.mutedForeground.color() }
        public static var destructive: Color { Colors.destructive.color() }
        public static var destructiveForeground: Color { Colors.destructiveForeground.color() }
        public static var border: Color { Colors.border.color() }
        public static var input: Color { Colors.input.color() }
        public static var ring: Color { Colors.ring.color() }
    }

    public enum UI {
        public static var background: PlatformColor { Colors.background.platformColor() }
        public static var foreground: PlatformColor { Colors.foreground.platformColor() }
        public static var card: PlatformColor { Colors.card.platformColor() }
        public static var cardForeground: PlatformColor { Colors.cardForeground.platformColor() }
        public static var primary: PlatformColor { Colors.primary.platformColor() }
        public static var primaryForeground: PlatformColor { Colors.primaryForeground.platformColor() }
        public static var accent: PlatformColor { Colors.accent.platformColor() }
        public static var accentForeground: PlatformColor { Colors.accentForeground.platformColor() }
        public static var muted: PlatformColor { Colors.muted.platformColor() }
        public static var mutedForeground: PlatformColor { Colors.mutedForeground.platformColor() }
        public static var destructive: PlatformColor { Colors.destructive.platformColor() }
        public static var destructiveForeground: PlatformColor { Colors.destructiveForeground.platformColor() }
        public static var border: PlatformColor { Colors.border.platformColor() }
        public static var input: PlatformColor { Colors.input.platformColor() }
        public static var ring: PlatformColor { Colors.ring.platformColor() }
    }

    public enum Typography {
        // RED STUB
        public static let fontName: String = ""
        public static var body: Font { .body }
        public static var passwordMonospaced: Font { .body }
        public static let isPasswordMonospaced: Bool = false
    }

    public enum Icon {
        // RED STUB: minimal stub placeholder values
        public static let passwordShow: String = ""
        public static let passwordHide: String = ""
        public static let clipboardCopy: String = ""
        public static let copyComplete: String = ""
        public static let regenerate: String = ""
        public static let vaultLock: String = ""
        public static let warning: String = ""
        public static let offlineBanner: String = ""
    }
}
