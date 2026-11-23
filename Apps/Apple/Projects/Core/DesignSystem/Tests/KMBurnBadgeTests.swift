import XCTest
@testable import KeyManageDesignSystem

@MainActor
final class KMBurnBadgeTests: XCTestCase {

    func testRemainingProgressRatio() {
        let fullBadge = KMBurnBadge(remainingSeconds: 30, totalSeconds: 30)
        XCTAssertEqual(fullBadge.progress, 1.0, accuracy: 0.001)

        let halfBadge = KMBurnBadge(remainingSeconds: 15, totalSeconds: 30)
        XCTAssertEqual(halfBadge.progress, 0.5, accuracy: 0.001)

        let zeroBadge = KMBurnBadge(remainingSeconds: 0, totalSeconds: 30)
        XCTAssertEqual(zeroBadge.progress, 0.0, accuracy: 0.001)
    }

    func testDefaultTotalSeconds() {
        let badge = KMBurnBadge(remainingSeconds: 20)
        XCTAssertEqual(badge.totalSeconds, 30)
    }
}
