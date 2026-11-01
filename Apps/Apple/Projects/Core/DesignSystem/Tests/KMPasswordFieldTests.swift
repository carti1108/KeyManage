import XCTest
import SwiftUI
@testable import KeyManageDesignSystem

@MainActor
final class KMPasswordFieldTests: XCTestCase {

    func testInitialState() {
        let engine = KMPasswordFieldEngine()
        XCTAssertTrue(engine.isSecured, "Initial password field should be secured (masked)")
        XCTAssertEqual(engine.placeholder, "비밀번호 입력")
        XCTAssertTrue(engine.showCopyAction, "Copy button action should be visible by default")
        XCTAssertEqual(engine.autoMaskSecondsRemaining, 0, "Timer should not be running initially")
    }

    func testToggleSecuredStarts10SecondTimer() {
        let engine = KMPasswordFieldEngine(text: "secretPass123")
        XCTAssertTrue(engine.isSecured)

        engine.toggleSecured()

        XCTAssertFalse(engine.isSecured, "Toggling secured state should reveal plain text")
        XCTAssertEqual(engine.autoMaskSecondsRemaining, 10, "Revealing plain text must start a 10s auto-mask timer")
    }

    func testCopyCallback() {
        var copyInvoked = false
        let engine = KMPasswordFieldEngine(text: "mySecret") {
            copyInvoked = true
        }

        engine.copyAction()

        XCTAssertTrue(copyInvoked, "Triggering copy action must invoke onCopy callback")
    }

    func testTextChangeResetsTimer() {
        let engine = KMPasswordFieldEngine(text: "pass")
        engine.toggleSecured()
        XCTAssertEqual(engine.autoMaskSecondsRemaining, 10)

        // Simulate 4 seconds passed
        engine.advanceTime(seconds: 4)
        XCTAssertEqual(engine.autoMaskSecondsRemaining, 6)

        // User edits text
        engine.updateText("passw")
        XCTAssertEqual(engine.autoMaskSecondsRemaining, 10, "User interaction / text modification must reset auto-mask timer to 10s")
    }

    func testAutoMaskAfter10Seconds() {
        let engine = KMPasswordFieldEngine(text: "secret123")
        engine.toggleSecured()
        XCTAssertFalse(engine.isSecured)

        // Advance 10 seconds of inactivity
        engine.advanceTime(seconds: 10)

        XCTAssertTrue(engine.isSecured, "Password field must automatically revert to secured mode after 10s of inactivity")
        XCTAssertEqual(engine.autoMaskSecondsRemaining, 0)
    }

    func testPasswordFieldViewIntegration() {
        var text = "mySecret"
        var copyCount = 0
        var textChangeCount = 0

        let engine = KMPasswordFieldEngine(
            text: text,
            onCopy: { copyCount += 1 },
            onTextChange: { _ in textChangeCount += 1 }
        )

        let field = KMPasswordField(
            text: Binding(get: { text }, set: { text = $0 }),
            engine: engine
        )

        XCTAssertTrue(field.isSecured)
        engine.toggleSecured()
        XCTAssertFalse(field.isSecured, "View isSecured must reflect engine state")

        engine.copyAction()
        XCTAssertEqual(copyCount, 1, "Copy callback must execute exactly once")

        engine.updateText("mySecretNew")
        XCTAssertEqual(textChangeCount, 1, "Text change callback must execute exactly once")
    }
}
