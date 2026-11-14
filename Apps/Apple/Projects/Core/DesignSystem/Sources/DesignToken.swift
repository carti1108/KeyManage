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

        private static let rgbaRegex = try? NSRegularExpression(
            pattern: #"rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d\.]+)\s*\)"#
        )

        public init(lightHex: String = "", darkHex: String = "") {
            self.lightHex = lightHex
            self.darkHex = darkHex
        }

        public func color(mode: Mode) -> Color {
            let hex = (mode == .dark) ? darkHex : lightHex
            let c = Self.parseColor(from: hex)
            return Color(.sRGB, red: c.r, green: c.g, blue: c.b, opacity: c.a)
        }

        public func color() -> Color {
            #if canImport(UIKit)
            return Color(uiColor: dynamicPlatformColor())
            #elseif canImport(AppKit)
            return Color(nsColor: dynamicPlatformColor())
            #else
            return color(mode: .dark)
            #endif
        }

        public func platformColor(mode: Mode = .dark) -> PlatformColor {
            let hex = (mode == .dark) ? darkHex : lightHex
            let c = Self.parseColor(from: hex)
            #if canImport(UIKit)
            return UIColor(red: CGFloat(c.r), green: CGFloat(c.g), blue: CGFloat(c.b), alpha: CGFloat(c.a))
            #elseif canImport(AppKit)
            return NSColor(srgbRed: CGFloat(c.r), green: CGFloat(c.g), blue: CGFloat(c.b), alpha: CGFloat(c.a))
            #endif
        }

        public func dynamicPlatformColor() -> PlatformColor {
            #if canImport(UIKit)
            return UIColor { traitCollection in
                let mode: Mode = (traitCollection.userInterfaceStyle == .light) ? .light : .dark
                return self.platformColor(mode: mode)
            }
            #elseif canImport(AppKit)
            return NSColor(name: nil) { appearance in
                let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
                return self.platformColor(mode: isDark ? .dark : .light)
            }
            #endif
        }

        private static func parseColor(from hexString: String) -> (r: Double, g: Double, b: Double, a: Double) {
            var str = hexString.trimmingCharacters(in: .whitespacesAndNewlines)
            if str.hasPrefix("rgba"), let regex = rgbaRegex {
                if let match = regex.firstMatch(in: str, range: NSRange(str.startIndex..., in: str)) {
                    let rStr = (str as NSString).substring(with: match.range(at: 1))
                    let gStr = (str as NSString).substring(with: match.range(at: 2))
                    let bStr = (str as NSString).substring(with: match.range(at: 3))
                    let aStr = (str as NSString).substring(with: match.range(at: 4))
                    let r = (Double(rStr) ?? 0) / 255.0
                    let g = (Double(gStr) ?? 0) / 255.0
                    let b = (Double(bStr) ?? 0) / 255.0
                    let a = Double(aStr) ?? 1.0
                    return (r, g, b, a)
                }
            }
            if str.hasPrefix("#") {
                str.removeFirst()
            }
            var hexNumber: UInt64 = 0
            let scanner = Scanner(string: str)
            if scanner.scanHexInt64(&hexNumber) {
                if str.count == 6 {
                    let r = Double((hexNumber & 0xFF0000) >> 16) / 255.0
                    let g = Double((hexNumber & 0x00FF00) >> 8) / 255.0
                    let b = Double(hexNumber & 0x0000FF) / 255.0
                    return (r, g, b, 1.0)
                } else if str.count == 8 {
                    let r = Double((hexNumber & 0xFF000000) >> 24) / 255.0
                    let g = Double((hexNumber & 0x00FF0000) >> 16) / 255.0
                    let b = Double((hexNumber & 0x0000FF00) >> 8) / 255.0
                    let a = Double(hexNumber & 0x000000FF) / 255.0
                    return (r, g, b, a)
                }
            }
            return (0, 0, 0, 1)
        }
    }

    // Colors·Spacing·Radius 값은 Generated/DesignToken+Generated.swift에서 생성한다 (ADR-0026).

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

    @MainActor
    public enum UI {
        public static var background: PlatformColor { Colors.background.dynamicPlatformColor() }
        public static var foreground: PlatformColor { Colors.foreground.dynamicPlatformColor() }
        public static var card: PlatformColor { Colors.card.dynamicPlatformColor() }
        public static var cardForeground: PlatformColor { Colors.cardForeground.dynamicPlatformColor() }
        public static var primary: PlatformColor { Colors.primary.dynamicPlatformColor() }
        public static var primaryForeground: PlatformColor { Colors.primaryForeground.dynamicPlatformColor() }
        public static var accent: PlatformColor { Colors.accent.dynamicPlatformColor() }
        public static var accentForeground: PlatformColor { Colors.accentForeground.dynamicPlatformColor() }
        public static var muted: PlatformColor { Colors.muted.dynamicPlatformColor() }
        public static var mutedForeground: PlatformColor { Colors.mutedForeground.dynamicPlatformColor() }
        public static var destructive: PlatformColor { Colors.destructive.dynamicPlatformColor() }
        public static var destructiveForeground: PlatformColor { Colors.destructiveForeground.dynamicPlatformColor() }
        public static var border: PlatformColor { Colors.border.dynamicPlatformColor() }
        public static var input: PlatformColor { Colors.input.dynamicPlatformColor() }
        public static var ring: PlatformColor { Colors.ring.dynamicPlatformColor() }
    }

    public enum Typography {
        public static let fontName: String = "SF Pro"
        public static var passwordMonospaced: Font { .system(.body, design: .monospaced) }
        public static let isPasswordMonospaced: Bool = true

        #if canImport(UIKit)
        public static var passwordMonospacedUI: UIFont {
            UIFont.monospacedSystemFont(ofSize: 16, weight: .medium)
        }
        #elseif canImport(AppKit)
        public static var passwordMonospacedUI: NSFont {
            NSFont.monospacedSystemFont(ofSize: 16, weight: .medium)
        }
        #endif
    }

    public enum Icon {
        public static let passwordShow: String = "eye"
        public static let passwordHide: String = "eye.slash"
        public static let clipboardCopy: String = "doc.on.doc"
        public static let copyComplete: String = "checkmark.circle.fill"
        public static let regenerate: String = "arrow.clockwise"
        public static let vaultLock: String = "lock.fill"
        public static let warning: String = "exclamationmark.triangle.fill"
        public static let offlineBanner: String = "wifi.slash"
    }
}
