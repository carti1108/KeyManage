import XCTest

/// 공유 코드 PasswordGeneratorMessages.COPY_FAILED와 같은 글자. UI 테스트 대상은 공유 프레임워크를 링크하지 않는다
private enum PasswordGeneratorCopyFailed {
	static let text = "클립보드에 복사하지 못했습니다."
}

/// 3단계 화면 흐름 (domain-migration-plan 12.3): 로그인, 항목 추가·복사·검색, 생성기 생성·복사, 자동 잠금 변경, 백업 내보내기.
/// 로컬 백엔드(localhost:8080)를 먼저 띄워 둔다. 계정은 실행마다 새로 만들고, `-resetForUITests`로 이전 실행의 기기 기록·자동 잠금 설정을 지우고 시작한다.
/// 요소는 접근성 식별자로 찾는다. 탭 막대 항목만 SwiftUI가 식별자를 넘기지 않아 탭 이름으로 찾는다
final class SharedFeatureFlowUITests: XCTestCase {
	private let networkTimeout: TimeInterval = 90
	private let screenTimeout: TimeInterval = 10

	@MainActor
	func testVaultGeneratorSettingsAndBackupFlows() {
		continueAfterFailure = false
		let app = XCUIApplication()
		app.launchArguments += ["-resetForUITests"]
		app.launch()
		let email = "stage3-\(UUID().uuidString.prefix(8).lowercased())@example.test"
		let password = "Stage3-" + UUID().uuidString

		signUp(app, email: email, password: password)
		logOutAndLogIn(app, email: email, password: password)
		addCopyAndSearchItem(app)
		generateAndCopy(app)
		changeAutoLock(app)
		exportBackup(app)
	}

	@MainActor
	private func signUp(_ app: XCUIApplication, email: String, password: String) {
		let switchMode = app.buttons["authSwitchModeButton"]
		XCTAssertTrue(switchMode.waitForExistence(timeout: screenTimeout), "초기화한 앱은 로그인 화면으로 시작해야 한다")
		switchMode.tap()
		enter(email, into: app.textFields["authEmailField"])
		enter(password, into: app.secureTextFields["authPasswordField"])
		enter(password, into: app.secureTextFields["authConfirmationField"])
		app.buttons["authSubmitButton"].tap()
		XCTAssertTrue(app.staticTexts["mainEmail"].waitForExistence(timeout: networkTimeout), "가입 뒤 볼트 화면이 보여야 한다")
	}

	/// 설정 탭에서 로그아웃하고 같은 계정으로 다시 로그인한다
	@MainActor
	private func logOutAndLogIn(_ app: XCUIApplication, email: String, password: String) {
		selectTab(app, "설정")
		let logout = app.buttons["settingsLogoutButton"]
		XCTAssertTrue(logout.waitForExistence(timeout: screenTimeout))
		logout.tap()
		let emailField = app.textFields["authEmailField"]
		XCTAssertTrue(emailField.waitForExistence(timeout: networkTimeout), "로그아웃하면 로그인 화면이 보여야 한다")
		enter(email, into: emailField)
		enter(password, into: app.secureTextFields["authPasswordField"])
		app.buttons["authSubmitButton"].tap()
		XCTAssertTrue(app.staticTexts["mainEmail"].waitForExistence(timeout: networkTimeout), "로그인 뒤 볼트 화면이 보여야 한다")
		XCTAssertEqual(app.staticTexts["mainEmail"].label, email)
	}

	@MainActor
	private func addCopyAndSearchItem(_ app: XCUIApplication) {
		selectTab(app, "볼트")
		let add = app.buttons["vaultAddButton"]
		XCTAssertTrue(add.waitForExistence(timeout: screenTimeout))
		add.tap()
		enter("GitHub", into: app.textFields["vaultFormServiceName"])
		enter("octocat", into: app.textFields["vaultFormUsername"])
		enter("Sample-Passw0rd-123", into: app.secureTextFields["vaultFormPassword"])
		XCTAssertTrue(app.descendants(matching: .any)["vaultFormSequentialNotice"].waitForExistence(timeout: screenTimeout), "이어지는 숫자 안내가 보여야 한다")
		app.buttons["vaultFormSaveButton"].tap()

		// 저장하면 새 항목의 상세 화면으로 간다(웹과 같다). 상세는 메인 툴바(mainEmail)를 가리므로 목록으로 돌아온다
		XCTAssertTrue(app.buttons["vaultEditButton"].waitForExistence(timeout: networkTimeout), "저장하면 새 항목의 상세가 보여야 한다")
		// 툴바의 잠금·로그아웃 버튼을 누르지 않게 시스템 뒤로 가기 버튼만 찾는다
		let back = app.navigationBars.buttons.matching(NSPredicate(format: "identifier == 'BackButton' OR label == '볼트'")).firstMatch
		XCTAssertTrue(back.waitForExistence(timeout: screenTimeout), "상세 화면에 뒤로 가기 버튼이 있어야 한다")
		back.tap()
		let item = app.buttons["vaultItem"]
		XCTAssertTrue(app.staticTexts["mainEmail"].waitForExistence(timeout: screenTimeout), "뒤로 가면 목록이 보여야 한다")
		XCTAssertTrue(item.waitForExistence(timeout: screenTimeout), "저장한 항목이 목록에 보여야 한다")
		// 테스트 실행기가 일반 클립보드를 읽으면 앱이 앞에서 밀려나 잠길 수 있어, 복사 실패 안내가 없는지만 본다
		app.buttons["vaultCopyPasswordButton"].firstMatch.tap()
		XCTAssertTrue(app.staticTexts["mainEmail"].exists, "비밀번호를 복사한 뒤에도 볼트가 열려 있어야 한다")
		app.buttons["vaultCopyUsernameButton"].firstMatch.tap()
		XCTAssertFalse(app.descendants(matching: .any)["vaultCopyError"].exists)
		XCTAssertTrue(app.staticTexts["mainEmail"].exists, "복사한 뒤에도 볼트가 열려 있어야 한다")

		let search = app.searchFields.firstMatch
		XCTAssertTrue(search.waitForExistence(timeout: screenTimeout))
		search.tap()
		search.typeText("zzz-none")
		XCTAssertTrue(app.descendants(matching: .any)["vaultEmptyMessage"].waitForExistence(timeout: screenTimeout), "맞는 항목이 없으면 안내가 보여야 한다")
		// 지운 뒤 다시 친다. 검색어 끝의 줄바꿈(검색 키)으로 키보드를 내려 탭 막대가 다시 보이게 한다. 취소 버튼 이름은 OS 버전마다 달라 쓰지 않는다
		search.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: "zzz-none".count))
		search.typeText("git\n")
		XCTAssertTrue(item.waitForExistence(timeout: screenTimeout), "검색어에 걸리는 항목이 보여야 한다")
		XCTAssertFalse(app.descendants(matching: .any)["vaultEmptyMessage"].exists)
	}

	@MainActor
	private func generateAndCopy(_ app: XCUIApplication) {
		selectTab(app, "생성기")
		let generated = app.staticTexts["generatedPassword"]
		XCTAssertTrue(generated.waitForExistence(timeout: screenTimeout))
		let first = generated.label
		app.buttons["regenerateButton"].tap()
		XCTAssertTrue(waitUntil { generated.label != first }, "다시 만들면 비밀번호가 바뀌어야 한다")
		app.buttons["copyGeneratedButton"].tap()
		XCTAssertFalse(app.staticTexts[PasswordGeneratorCopyFailed.text].exists, "복사 실패 안내가 없어야 한다")
	}

	@MainActor
	private func changeAutoLock(_ app: XCUIApplication) {
		selectTab(app, "설정")
		let picker = app.buttons["autoLockPicker"]
		XCTAssertTrue(picker.waitForExistence(timeout: screenTimeout))
		XCTAssertTrue(picker.label.contains("5분"), "기본 자동 잠금은 5분이다")
		picker.tap()
		let oneMinute = app.buttons["autoLockChoice-60"]
		XCTAssertTrue(oneMinute.waitForExistence(timeout: screenTimeout))
		oneMinute.tap()
		XCTAssertTrue(waitUntil { picker.label.contains("1분") }, "고른 자동 잠금 시간이 보여야 한다")
	}

	@MainActor
	private func exportBackup(_ app: XCUIApplication) {
		let export = app.buttons["exportBackupButton"]
		XCTAssertTrue(export.waitForExistence(timeout: screenTimeout))
		export.tap()
		// 확인 창 버튼은 접근성 트리에 두 번 잡힐 수 있어 첫 요소를 쓴다
		let confirm = app.buttons["exportConfirmButton"].firstMatch
		XCTAssertTrue(confirm.waitForExistence(timeout: screenTimeout))
		confirm.tap()
		// 저장한 파일을 내보내는 문서 선택기가 뜬다. 파일 이름(keymanage-backup-…)이 선택기에 보여야 한다
		let fileName = NSPredicate(format: "label CONTAINS[c] 'keymanage'")
		let exported = app.descendants(matching: .any).matching(fileName).firstMatch
		XCTAssertTrue(exported.waitForExistence(timeout: networkTimeout), "내보낸 백업 파일이 문서 선택기에 보여야 한다")
		XCTAssertFalse(app.descendants(matching: .any)["settingsError"].exists)
	}

	@MainActor
	private func selectTab(_ app: XCUIApplication, _ name: String) {
		let tab = app.tabBars.buttons[name]
		XCTAssertTrue(tab.waitForExistence(timeout: screenTimeout), "\(name) 탭이 보여야 한다")
		tab.tap()
	}

	@MainActor
	private func enter(_ text: String, into field: XCUIElement) {
		XCTAssertTrue(field.waitForExistence(timeout: screenTimeout))
		field.tap()
		let focused = XCTNSPredicateExpectation(predicate: NSPredicate(format: "hasKeyboardFocus == true"), object: field)
		XCTAssertEqual(XCTWaiter().wait(for: [focused], timeout: screenTimeout), .completed)
		field.typeText(text)
	}

	@MainActor
	private func waitUntil(_ condition: @escaping () -> Bool) -> Bool {
		let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in condition() }, object: nil)
		return XCTWaiter().wait(for: [expectation], timeout: screenTimeout) == .completed
	}
}
