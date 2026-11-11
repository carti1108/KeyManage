import XCTest
@testable import KeyManageDesignSystem

@MainActor
final class DesignTokenTests: XCTestCase {

    func testColorHexValues() {
        // 15 color tokens specification verification
        XCTAssertEqual(DesignToken.Colors.background.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.background.darkHex, "#000000")

        XCTAssertEqual(DesignToken.Colors.foreground.lightHex, "#0A0A0A")
        XCTAssertEqual(DesignToken.Colors.foreground.darkHex, "#FAFAFA")

        XCTAssertEqual(DesignToken.Colors.card.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.card.darkHex, "#141414")

        XCTAssertEqual(DesignToken.Colors.cardForeground.lightHex, "#0A0A0A")
        XCTAssertEqual(DesignToken.Colors.cardForeground.darkHex, "#FAFAFA")

        XCTAssertEqual(DesignToken.Colors.primary.lightHex, "#0A0A0A")
        XCTAssertEqual(DesignToken.Colors.primary.darkHex, "#FAFAFA")

        XCTAssertEqual(DesignToken.Colors.primaryForeground.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.primaryForeground.darkHex, "#0A0A0A")

        XCTAssertEqual(DesignToken.Colors.accent.lightHex, "#059669")
        XCTAssertEqual(DesignToken.Colors.accent.darkHex, "#10B981")

        XCTAssertEqual(DesignToken.Colors.accentForeground.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.accentForeground.darkHex, "#0A0A0A")

        XCTAssertEqual(DesignToken.Colors.muted.lightHex, "#F5F5F5")
        XCTAssertEqual(DesignToken.Colors.muted.darkHex, "#262626")

        XCTAssertEqual(DesignToken.Colors.mutedForeground.lightHex, "#737373")
        XCTAssertEqual(DesignToken.Colors.mutedForeground.darkHex, "#A3A3A3")

        XCTAssertEqual(DesignToken.Colors.destructive.lightHex, "#DC2626")
        XCTAssertEqual(DesignToken.Colors.destructive.darkHex, "#EF4444")

        XCTAssertEqual(DesignToken.Colors.destructiveForeground.lightHex, "#FFFFFF")
        XCTAssertEqual(DesignToken.Colors.destructiveForeground.darkHex, "#FFFFFF")

        XCTAssertEqual(DesignToken.Colors.border.lightHex, "#E5E5E5")
        let isBorderDarkValid = DesignToken.Colors.border.darkHex == "rgba(255, 255, 255, 0.12)"
            || DesignToken.Colors.border.darkHex == "#FFFFFF1F"
        XCTAssertTrue(isBorderDarkValid, "border dark mode should be rgba(255, 255, 255, 0.12) or #FFFFFF1F")

        XCTAssertEqual(DesignToken.Colors.input.lightHex, "#E5E5E5")
        XCTAssertEqual(DesignToken.Colors.input.darkHex, "#262626")

        XCTAssertEqual(DesignToken.Colors.ring.lightHex, "#0A0A0A")
        XCTAssertEqual(DesignToken.Colors.ring.darkHex, "#FAFAFA")
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
