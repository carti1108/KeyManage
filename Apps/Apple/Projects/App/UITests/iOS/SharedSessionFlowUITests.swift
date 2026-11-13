import XCTest

/// 공유 Kotlin RIB 트리로 띄운 iOS 앱이 로컬 백엔드(localhost:8080)에 가입 → 잠금 → 해제하는지 확인한다 (domain-migration-plan 12.3의 2단계).
/// 백엔드를 먼저 띄워 둔다. 계정은 실행마다 새로 만든다
final class SharedSessionFlowUITests: XCTestCase {
	/// 키 유도(PBKDF2)와 서버 왕복이 디버그 빌드 시뮬레이터에서 오래 걸릴 수 있다
	private let networkTimeout: TimeInterval = 90
	private let screenTimeout: TimeInterval = 10
	@MainActor
	func testSignUpThenLockThenUnlock() {
		continueAfterFailure = false
		let app = XCUIApplication()
		app.launch()
		let email = "stage2-\(UUID().uuidString.prefix(8).lowercased())@example.test"
		let password = "Stage2-" + UUID().uuidString

		// 이전 실행이 남긴 기기 기록이 있으면 잠금 화면으로 시작한다. 로그아웃해 로그인 화면에서 시작한다
		let switchMode = app.buttons["authSwitchModeButton"]
		let leftoverLogout = app.buttons["unlockLogoutButton"]
		XCTAssertTrue(switchMode.waitForExistence(timeout: screenTimeout) || leftoverLogout.exists)
		if leftoverLogout.exists {
			leftoverLogout.tap()
			XCTAssertTrue(switchMode.waitForExistence(timeout: networkTimeout))
		}

		// 가입
		switchMode.tap()
		type(email, into: app.textFields["authEmailField"])
		type(password, into: app.secureTextFields["authPasswordField"])
		type(password, into: app.secureTextFields["authConfirmationField"])
		app.buttons["authSubmitButton"].tap()

		let mainEmail = app.staticTexts["mainEmail"]
		XCTAssertTrue(mainEmail.waitForExistence(timeout: networkTimeout), "가입 뒤 볼트 화면이 보여야 한다")
		XCTAssertEqual(mainEmail.label, email)

		// 잠금
		app.buttons["mainLockButton"].tap()
		XCTAssertTrue(app.staticTexts["unlockTitle"].waitForExistence(timeout: screenTimeout))
		XCTAssertEqual(app.staticTexts["unlockEmail"].label, email)

		// 틀린 비밀번호는 열리지 않는다
		type(password + "x", into: app.secureTextFields["unlockPasswordField"])
		app.buttons["unlockButton"].tap()
		XCTAssertTrue(app.descendants(matching: .any)["unlockError"].waitForExistence(timeout: networkTimeout))
		XCTAssertFalse(mainEmail.exists)

		// 해제
		let passwordField = app.secureTextFields["unlockPasswordField"]
		clear(passwordField)
		type(password, into: passwordField)
		app.buttons["unlockButton"].tap()
		XCTAssertTrue(mainEmail.waitForExistence(timeout: networkTimeout), "올바른 비밀번호로 볼트가 열려야 한다")
	}

	@MainActor
	private func type(_ text: String, into field: XCUIElement) {
		XCTAssertTrue(field.waitForExistence(timeout: screenTimeout))
		field.tap()
		waitForKeyboardFocus(field)
		field.typeText(text)
	}

	@MainActor
	private func clear(_ field: XCUIElement) {
		XCTAssertTrue(field.waitForExistence(timeout: screenTimeout))
		field.tap()
		waitForKeyboardFocus(field)
		// 보안 입력 칸의 값은 글자 수만큼의 가림 문자다
		let length = (field.value as? String)?.count ?? 0
		field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: length))
	}

	/// 누른 칸이 입력을 받기 전에 치면 글자가 이전 칸으로 들어간다
	@MainActor
	private func waitForKeyboardFocus(_ field: XCUIElement) {
		let focused = NSPredicate(format: "hasKeyboardFocus == true")
		let expectation = XCTNSPredicateExpectation(predicate: focused, object: field)
		XCTAssertEqual(XCTWaiter().wait(for: [expectation], timeout: screenTimeout), .completed)
	}
}
