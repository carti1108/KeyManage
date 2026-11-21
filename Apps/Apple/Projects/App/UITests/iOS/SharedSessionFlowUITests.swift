import XCTest

/// iOS 앱이 공유 Kotlin RIB 트리와 로컬 백엔드로 가입 → 볼트 → 항목 추가 → 잠금 → 잠금 해제까지 가는지 본다.
/// 실행 전에 이 Mac에서 백엔드를 띄운다(`Back`에서 `./gradlew :app:bootRun`). 서버가 없으면 건너뛴다
@MainActor
final class SharedSessionFlowUITests: XCTestCase {
	private let password = "correct-horse-battery-staple"

	override func setUp() async throws {
		continueAfterFailure = false
		let isRunning = await Self.isBackendRunning()
		try XCTSkipUnless(isRunning, "로컬 백엔드(http://localhost:8080)가 없음")
	}

	func test_register_addItem_lock_unlock() {
		let app = XCUIApplication()
		app.launch()

		// 앞선 실행의 계정이 기기 기록에 남아 있으면 잠금 화면에서 시작한다(SEC-003 규칙 9). 로그아웃하고 새로 가입한다
		let switchMode = app.buttons["auth.switchMode"]
		let logout = app.buttons["unlock.logout"]
		XCTAssertTrue(switchMode.waitForExistence(timeout: 10) || logout.exists)
		if logout.exists {
			logout.tap()
			XCTAssertTrue(switchMode.waitForExistence(timeout: 10), "로그아웃 뒤 로그인 화면이 보이지 않음")
		}

		// 가입
		switchMode.tap()
		type(app.textFields["auth.email"], "ios-\(Int(Date().timeIntervalSince1970))@example.com")
		type(app.secureTextFields.element(boundBy: 0), password)
		type(app.secureTextFields.element(boundBy: 1), password)
		app.buttons["auth.submit"].tap()

		// 볼트 탭
		let add = app.buttons["vault.add"]
		XCTAssertTrue(add.waitForExistence(timeout: 30), "가입 뒤 볼트 화면이 보이지 않음")
		add.tap()
		type(app.textFields["vault.form.service_name"], "GitHub")
		type(app.secureTextFields.firstMatch, "gh-password-123")
		app.buttons["vault.form.save"].tap()
		// 저장하면 공유 VaultList가 그 항목의 상세로 간다
		XCTAssertTrue(app.buttons["vault.edit"].waitForExistence(timeout: 15), "저장 뒤 상세 화면이 보이지 않음")
		app.buttons["vault.back"].tap()
		XCTAssertTrue(app.buttons["vault.item.GitHub"].waitForExistence(timeout: 5), "저장한 항목이 목록에 없음")

		// 설정 → 잠그기
		app.buttons["main.tab.settings"].tap()
		let lock = app.buttons["settings.lock"]
		XCTAssertTrue(lock.waitForExistence(timeout: 5))
		lock.tap()

		// 잠금 해제
		let unlock = app.buttons["unlock.submit"]
		XCTAssertTrue(unlock.waitForExistence(timeout: 10), "잠금 화면이 보이지 않음")
		type(app.secureTextFields.firstMatch, password)
		unlock.tap()
		XCTAssertTrue(app.buttons["vault.item.GitHub"].waitForExistence(timeout: 30), "잠금 해제 뒤 항목이 보이지 않음")

		// 복사하면 30초 뒤 비운다는 안내와 남은 초가 보인다 (COM-001)
		app.buttons["GitHub 비밀번호 복사"].tap()
		XCTAssertTrue(app.descendants(matching: .any)["main.clipboardNotice"].waitForExistence(timeout: 5), "복사 안내가 보이지 않음")
	}

	private func type(_ field: XCUIElement, _ text: String) {
		XCTAssertTrue(field.waitForExistence(timeout: 10), "입력 칸 없음: \(field)")
		field.tap()
		field.typeText(text)
	}

	private static func isBackendRunning() async -> Bool {
		var request = URLRequest(url: URL(string: "http://localhost:8080/api/v1/auth/login")!, timeoutInterval: 2)
		request.httpMethod = "POST"
		return (try? await URLSession.shared.data(for: request)) != nil
	}
}
