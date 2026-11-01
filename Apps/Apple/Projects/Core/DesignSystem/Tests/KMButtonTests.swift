import XCTest
@testable import KeyManageDesignSystem

@MainActor
final class KMButtonTests: XCTestCase {

    func testButtonSizesHeight() {
        XCTAssertEqual(KMButtonSize.sm.height, 32.0, "sm button height must be 32pt")
        XCTAssertEqual(KMButtonSize.md.height, 40.0, "md button height must be 40pt")
        XCTAssertEqual(KMButtonSize.lg.height, 48.0, "lg button height must be 48pt")
    }

    func testButtonVariants() {
        let expectedVariants: [KMButtonVariant] = [.primary, .outline, .destructive, .ghost]
        XCTAssertEqual(KMButtonVariant.allCases, expectedVariants)
    }

    func testButtonDisabledState() {
        let button = KMButton("저장", isDisabled: true) {}
        XCTAssertFalse(button.isInteractive, "Disabled button must not be interactive")
    }

    func testButtonLoadingState() {
        let button = KMButton("생성 중", isLoading: true) {}
        XCTAssertFalse(button.isInteractive, "Loading button must not be interactive")
    }

    func testButtonDefaultValues() {
        let button = KMButton("확인") {}
        XCTAssertEqual(button.variant, .primary)
        XCTAssertEqual(button.size, .md)
        XCTAssertNil(button.icon)
        XCTAssertFalse(button.isLoading)
        XCTAssertFalse(button.isDisabled)
        XCTAssertTrue(button.isInteractive)
    }
}
