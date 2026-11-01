import XCTest
@testable import KeyManageDesignSystem

@MainActor
final class KMGaugeBarTests: XCTestCase {

    func testWeakScoreRange() {
        let scores = [0, 30, 59]
        for score in scores {
            let gauge = KMGaugeBar(score: score)
            XCTAssertEqual(gauge.level, .weak, "Score \(score) should be weak level")
            XCTAssertEqual(gauge.statusText, "취약함", "Score \(score) label should be '취약함'")
            XCTAssertEqual(gauge.colorToken, DesignToken.Colors.destructive, "Score \(score) color should be destructive")
        }
    }

    func testModerateScoreRange() {
        let scores = [60, 70, 79]
        for score in scores {
            let gauge = KMGaugeBar(score: score)
            XCTAssertEqual(gauge.level, .moderate, "Score \(score) should be moderate level")
            XCTAssertEqual(gauge.statusText, "보통", "Score \(score) label should be '보통'")
            XCTAssertEqual(gauge.colorToken, DesignToken.Colors.primary, "Score \(score) color should be primary")
        }
    }

    func testStrongScoreRange() {
        let scores = [80, 90, 100]
        for score in scores {
            let gauge = KMGaugeBar(score: score)
            XCTAssertEqual(gauge.level, .strong, "Score \(score) should be strong level")
            XCTAssertEqual(gauge.statusText, "초강력", "Score \(score) label should be '초강력'")
            XCTAssertEqual(gauge.colorToken, DesignToken.Colors.accent, "Score \(score) color should be accent")
        }
    }

    func testScoreClamping() {
        let negativeGauge = KMGaugeBar(score: -10)
        XCTAssertEqual(negativeGauge.clampedScore, 0)

        let overflowGauge = KMGaugeBar(score: 150)
        XCTAssertEqual(overflowGauge.clampedScore, 100)
    }

    func testDefaultValues() {
        let gauge = KMGaugeBar(score: 75)
        XCTAssertTrue(gauge.showLabel)
        XCTAssertTrue(gauge.animated)
    }
}
