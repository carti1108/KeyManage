import XCTest
@testable import KeyManageDesignSystem

@MainActor
final class DesignTokenTests: XCTestCase {

    func testColorHexValues() {
        // 15 color tokens specification verification
        XCTAssertEqual(DesignToken.Colors.background.lightHex, "#F8FAFC")
        XCTAssertEqual(DesignToken.Colors.background.darkHex, "#0F172A")

        XCTAssertEqual(DesignToken.Colors.foreground.lightHex, "#0F172A")
        XCTAssertEqual(DesignToken.Colors.foreground.darkHex, "#F8FAFC")

        XCTAssertEqual(DesignToken.Colors.card.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.card.darkHex, "#1E293B")

        XCTAssertEqual(DesignToken.Colors.cardForeground.lightHex, "#0F172A")
        XCTAssertEqual(DesignToken.Colors.cardForeground.darkHex, "#F8FAFC")

        XCTAssertEqual(DesignToken.Colors.primary.lightHex, "#1E3A5F")
        XCTAssertEqual(DesignToken.Colors.primary.darkHex, "#38BDF8")

        XCTAssertEqual(DesignToken.Colors.primaryForeground.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.primaryForeground.darkHex, "#0F172A")

        XCTAssertEqual(DesignToken.Colors.accent.lightHex, "#059669")
        XCTAssertEqual(DesignToken.Colors.accent.darkHex, "#10B981")

        XCTAssertEqual(DesignToken.Colors.accentForeground.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.accentForeground.darkHex, "#0F172A")

        XCTAssertEqual(DesignToken.Colors.muted.lightHex, "#F1F5F9")
        XCTAssertEqual(DesignToken.Colors.muted.darkHex, "#1E293B")

        XCTAssertEqual(DesignToken.Colors.mutedForeground.lightHex, "#64748B")
        XCTAssertEqual(DesignToken.Colors.mutedForeground.darkHex, "#94A3B8")

        XCTAssertEqual(DesignToken.Colors.destructive.lightHex, "#DC2626")
        XCTAssertEqual(DesignToken.Colors.destructive.darkHex, "#EF4444")

        XCTAssertEqual(DesignToken.Colors.destructiveForeground.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.destructiveForeground.darkHex, "#FFFFFF")

        XCTAssertEqual(DesignToken.Colors.border.lightHex, "#E2E8F0")
        let isBorderDarkValid = DesignToken.Colors.border.darkHex == "rgba(255, 255, 255, 0.08)"
            || DesignToken.Colors.border.darkHex == "#FFFFFF14"
        XCTAssertTrue(isBorderDarkValid, "border dark mode should be rgba(255, 255, 255, 0.08) or #FFFFFF14")

        XCTAssertEqual(DesignToken.Colors.input.lightHex, "#E2E8F0")
        XCTAssertEqual(DesignToken.Colors.input.darkHex, "#334155")

        XCTAssertEqual(DesignToken.Colors.ring.lightHex, "#38BDF8")
        XCTAssertEqual(DesignToken.Colors.ring.darkHex, "#38BDF8")
    }

    func testIconSymbols() {
        XCTAssertEqual(DesignToken.Icon.passwordShow, "eye")
        XCTAssertEqual(DesignToken.Icon.passwordHide, "eye.slash")
        XCTAssertEqual(DesignToken.Icon.clipboardCopy, "doc.on.doc")
        XCTAssertEqual(DesignToken.Icon.copyComplete, "checkmark.circle.fill")
        XCTAssertEqual(DesignToken.Icon.regenerate, "arrow.clockwise")
        XCTAssertEqual(DesignToken.Icon.vaultLock, "lock.fill")
        XCTAssertEqual(DesignToken.Icon.warning, "exclamationmark.triangle.fill")
        XCTAssertEqual(DesignToken.Icon.offlineBanner, "wifi.slash")
    }

    func testTypographyTokens() {
        XCTAssertEqual(DesignToken.Typography.fontName, "SF Pro")
        XCTAssertTrue(DesignToken.Typography.isPasswordMonospaced, "Password font should be monospaced")
    }
}
