import XCTest

/// 공유 Kotlin RIB 트리로 띄운 macOS 앱이 로컬 백엔드(localhost:8080)에 가입 → 잠금 → 해제하는지 확인한다 (domain-migration-plan 12.5 macOS 전환).
/// iOS SharedSessionFlowUITests와 같은 흐름이다. 백엔드를 먼저 띄워 둔다. 계정은 실행마다 새로 만들고, `-resetForUITests`로 이전 실행의 기기 기록을 지우고 시작한다
final class MacSessionFlowUITests: XCTestCase {
	/// 키 유도(PBKDF2)와 서버 왕복이 디버그 빌드에서 오래 걸릴 수 있다
	private let networkTimeout: TimeInterval = 90
	private let screenTimeout: TimeInterval = 10

	@MainActor
	func testSignUpThenLockThenUnlock() {
		continueAfterFailure = false
		let app = XCUIApplication()
		app.launchArguments += ["-resetForUITests"]
		app.launch()
		let email = "mac-\(UUID().uuidString.prefix(8).lowercased())@example.test"
		let password = "Mac-" + UUID().uuidString

		// 실행 인자로 기기 기록을 지우지만, 초기화가 막힌 경우를 대비해 남은 잠금 화면이면 로그아웃해 로그인 화면에서 시작한다
		let switchMode = app.buttons["authSwitchModeButton"]
		let leftoverLogout = app.buttons["unlockLogoutButton"]
		XCTAssertTrue(switchMode.waitForExistence(timeout: screenTimeout) || leftoverLogout.exists)
		if leftoverLogout.exists {
			leftoverLogout.click()
			XCTAssertTrue(switchMode.waitForExistence(timeout: networkTimeout))
		}

		// 가입
		switchMode.click()
		type(email, into: app.textFields["authEmailField"])
		type(password, into: app.secureTextFields["authPasswordField"])
		type(password, into: app.secureTextFields["authConfirmationField"])
		app.buttons["authSubmitButton"].click()

		let mainEmail = app.staticTexts["mainEmail"]
		XCTAssertTrue(mainEmail.waitForExistence(timeout: networkTimeout), "가입 뒤 볼트 화면이 보여야 한다")
		XCTAssertEqual(mainEmail.value as? String ?? mainEmail.label, email)

		// 잠금
		let lock = app.buttons["mainLockButton"]
		XCTAssertTrue(lock.waitForExistence(timeout: screenTimeout))
		lock.click()
		XCTAssertTrue(app.staticTexts["unlockTitle"].waitForExistence(timeout: screenTimeout))

		// 틀린 비밀번호는 열리지 않는다
		let passwordField = app.secureTextFields["unlockPasswordField"]
		type(password + "x", into: passwordField)
		let unlock = app.buttons["unlockButton"]
		XCTAssertTrue(unlock.waitForExistence(timeout: screenTimeout))
		unlock.click()
		XCTAssertTrue(app.descendants(matching: .any)["unlockError"].waitForExistence(timeout: networkTimeout))
		XCTAssertFalse(mainEmail.exists)

		// 해제
		clear(passwordField)
		type(password, into: passwordField)
		unlock.click()
		XCTAssertTrue(mainEmail.waitForExistence(timeout: networkTimeout), "올바른 비밀번호로 볼트가 열려야 한다")
	}

	@MainActor
	private func type(_ text: String, into field: XCUIElement) {
		XCTAssertTrue(field.waitForExistence(timeout: screenTimeout))
		field.click()
		waitForKeyboardFocus(field)
		field.typeText(text)
	}

	@MainActor
	private func clear(_ field: XCUIElement) {
		XCTAssertTrue(field.waitForExistence(timeout: screenTimeout))
		field.click()
		waitForKeyboardFocus(field)
		field.typeKey("a", modifierFlags: .command)
		field.typeKey(.delete, modifierFlags: [])
	}

	/// 누른 칸이 입력을 받기 전에 치면 글자가 이전 칸으로 들어간다
	@MainActor
	private func waitForKeyboardFocus(_ field: XCUIElement) {
		let focused = NSPredicate(format: "hasKeyboardFocus == true")
		let expectation = XCTNSPredicateExpectation(predicate: focused, object: field)
		XCTAssertEqual(XCTWaiter().wait(for: [expectation], timeout: screenTimeout), .completed)
	}
}
