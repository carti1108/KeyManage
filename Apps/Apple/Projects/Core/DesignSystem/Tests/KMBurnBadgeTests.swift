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

    func testOnBurnedCallbackFiredAtZero() {
        var burnedFired = false
        let model = KMBurnBadgeModel(remainingSeconds: 1, totalSeconds: 30) {
            burnedFired = true
        }

        XCTAssertFalse(burnedFired, "Callback should not fire while time remains")

        model.remainingSeconds = 0
        model.tick()

        XCTAssertTrue(burnedFired, "Callback must fire when remainingSeconds reaches 0")
    }

    func testOnBurnedNotFiredWhenTimeRemains() {
        var burnedFired = false
        let model = KMBurnBadgeModel(remainingSeconds: 25, totalSeconds: 30) {
            burnedFired = true
        }

        model.tick()

        XCTAssertFalse(burnedFired, "Callback must not fire when remainingSeconds is positive")
    }

    func testOnBurnedCallbackOnlyFiresOnceAtZero() {
        var burnCount = 0
        let model = KMBurnBadgeModel(remainingSeconds: 1, totalSeconds: 30) {
            burnCount += 1
        }

        model.remainingSeconds = 0
        model.tick()
        XCTAssertEqual(burnCount, 1)

        model.tick()
        model.tick()
        XCTAssertEqual(burnCount, 1, "onBurned must fire at most once")
    }
}
