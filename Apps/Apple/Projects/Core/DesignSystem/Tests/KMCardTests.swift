import XCTest
import SwiftUI
@testable import KeyManageDesignSystem

@MainActor
final class KMCardTests: XCTestCase {

    func testCardPaddingValues() {
        XCTAssertEqual(KMCardPadding.sm.value, 12.0, "sm padding must be 12pt")
        XCTAssertEqual(KMCardPadding.md.value, 16.0, "md padding must be 16pt")
        XCTAssertEqual(KMCardPadding.lg.value, 20.0, "lg padding must be 20pt")
    }

    func testCardDefaultValues() {
        let card = KMCard {
            Text("Content")
        }
        XCTAssertEqual(card.padding, .md, "Default padding must be md (16pt)")
        XCTAssertFalse(card.isInteractive, "Default isInteractive must be false")
    }

    func testCardInteractiveProperty() {
        let interactiveCard = KMCard(padding: .lg, isInteractive: true) {
            Text("Interactive Content")
        }
        XCTAssertEqual(interactiveCard.padding, .lg)
        XCTAssertTrue(interactiveCard.isInteractive, "Interactive card should have isInteractive = true")
    }
}
