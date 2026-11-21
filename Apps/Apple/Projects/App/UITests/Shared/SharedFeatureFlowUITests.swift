import XCTest

/// 앱 흐름 중 SharedSessionFlowUITests가 보지 않는 것: 검색·수정·삭제, 자동 잠금, 생체 해제, 백업 내보내기 창, 오프라인 잠금 해제.
/// 서버가 필요한 테스트는 로컬 백엔드(http://localhost:8080)가 없으면 건너뛰고, 오프라인 테스트는 백엔드가 떠 있으면 건너뛴다.
/// 생체 테스트는 시뮬레이터에 Face ID를 등록하고 호스트가 일치 신호를 보낼 때만 돈다(실행 환경 변수 TEST_RUNNER_KEYMANAGE_FACE_ID=1)
@MainActor
final class SharedFeatureFlowUITests: XCTestCase {
	private let password = "correct-horse-battery-staple"
	private let app = XCUIApplication()

	override func setUp() async throws {
		continueAfterFailure = false
	}

	func test_search_edit_delete() async throws {
		try await requireBackend(true)
		signUpFresh()
		addItem("GitHub")
		addItem("Bank")

		// 검색: 걸리지 않는 항목은 목록에서 빠진다
		type(app.textFields["vault.search"], "git")
		XCTAssertTrue(app.buttons["vault.item.GitHub"].waitForExistence(timeout: 5))
		XCTAssertFalse(app.buttons["vault.item.Bank"].exists, "검색어에 걸리지 않는 항목이 보임")
		clear(app.textFields["vault.search"])

		// 수정: 서비스 이름을 바꾸면 목록 이름도 바뀐다
		app.buttons["vault.item.GitHub"].tap()
		app.buttons["vault.edit"].tap()
		let name = app.textFields["vault.form.service_name"]
		clear(name)
		type(name, "GitLab")
		app.buttons["vault.form.save"].tap()
		XCTAssertTrue(app.buttons["vault.edit"].waitForExistence(timeout: 15))
		app.buttons["vault.back"].tap()
		XCTAssertTrue(app.buttons["vault.item.GitLab"].waitForExistence(timeout: 5), "수정한 이름이 목록에 없음")

		// 삭제: 확인하면 목록에서 사라진다
		app.buttons["vault.item.GitLab"].tap()
		app.buttons["vault.delete"].tap()
		app.alerts.buttons["삭제"].tap()
		XCTAssertTrue(app.buttons["vault.item.Bank"].waitForExistence(timeout: 15))
		XCTAssertFalse(app.buttons["vault.item.GitLab"].exists, "삭제한 항목이 남아 있음")
	}

	#if os(iOS)
	func test_autoLockImmediate_locksWhenBackgrounded() async throws {
		try await requireBackend(true)
		signUpFresh()
		app.buttons["main.tab.settings"].tap()
		app.buttons["settings.autoLock"].tap()
		app.buttons["즉시"].tap()
		XCTAssertTrue(app.staticTexts["즉시"].waitForExistence(timeout: 5), "자동 잠금 값이 바뀌지 않음")

		XCUIDevice.shared.press(.home)
		app.activate()

		XCTAssertTrue(app.buttons["unlock.submit"].waitForExistence(timeout: 10), "백그라운드로 보냈다 돌아왔는데 잠기지 않음")
	}
	#endif

	func test_biometricOffer_enable_thenUnlockWithFaceID() async throws {
		try XCTSkipUnless(ProcessInfo.processInfo.environment["KEYMANAGE_FACE_ID"] == "1", "Face ID 일치 신호를 보내는 호스트가 없음")
		try await requireBackend(true)
		signUpFresh(expectVault: false)

		// 가입 직후 안내에서 켠다 (SEC-003 값 표). 인증 창은 호스트의 일치 신호로 통과한다
		let accept = app.alerts.buttons["켜기"]
		XCTAssertTrue(accept.waitForExistence(timeout: 30), "가입 직후 생체 인증 안내가 보이지 않음")
		accept.tap()
		XCTAssertTrue(app.buttons["vault.add"].waitForExistence(timeout: 30))

		lockFromSettings()
		let biometric = app.buttons["unlock.biometric"]
		XCTAssertTrue(biometric.waitForExistence(timeout: 10), "잠금 화면에 생체 해제 버튼이 없음")
		biometric.tap()
		XCTAssertTrue(app.buttons["vault.add"].waitForExistence(timeout: 30), "생체 해제로 볼트가 열리지 않음")
	}

	/// 내보낸 파일로 지운 항목을 되살린다 (VLT-004). 문서 선택 창은 다른 프로세스 화면이라 이름·라벨로 찾는다
	func test_exportBackup_thenRestoreDeletedItem() async throws {
		try await requireBackend(true)
		signUpFresh()
		addItem("GitHub")

		// 내보내기: 확인 → 저장 창 → 저장
		app.buttons["main.tab.settings"].tap()
		app.buttons["settings.export"].tap()
		app.alerts.buttons["내보내기"].tap()
		let save = app.buttons.matching(NSPredicate(format: "label IN %@", ["저장", "Save"])).firstMatch
		XCTAssertTrue(save.waitForExistence(timeout: 10), "백업 저장 창이 뜨지 않음")
		save.tap()
		let replace = app.buttons.matching(NSPredicate(format: "label IN %@", ["대치", "Replace"])).firstMatch
		if replace.waitForExistence(timeout: 2) { replace.tap() }
		XCTAssertTrue(app.buttons["settings.export"].waitForExistence(timeout: 10))
		XCTAssertTrue(wait { self.app.buttons["settings.export"].isEnabled }, "내보내기가 끝나지 않음")
		XCTAssertFalse(app.staticTexts["settings.message"].exists, "내보내기 뒤 오류가 보임")

		// 항목을 지운다
		app.buttons["main.tab.vault"].tap()
		app.buttons["vault.item.GitHub"].tap()
		app.buttons["vault.delete"].tap()
		app.alerts.buttons["삭제"].tap()
		XCTAssertTrue(app.staticTexts["vault.empty"].waitForExistence(timeout: 15), "삭제 뒤 빈 볼트가 아님")

		// 복원: 파일 선택 → 비밀번호 → 복원
		app.buttons["main.tab.settings"].tap()
		app.buttons["settings.restore"].tap()
		let file = app.descendants(matching: .any).matching(NSPredicate(format: "label BEGINSWITH 'keymanage-backup-'")).firstMatch
		XCTAssertTrue(file.waitForExistence(timeout: 15), "선택 창에 내보낸 백업 파일이 없음")
		file.tap()
		type(app.secureTextFields["settings.modal.백업 파일 비밀번호"], password)
		app.buttons["settings.modal.submit"].tap()
		XCTAssertTrue(app.staticTexts["백업에서 항목 1개를 복원했습니다."].waitForExistence(timeout: 30), "복원 완료 안내가 없음")
		app.buttons["main.tab.vault"].tap()
		XCTAssertTrue(app.buttons["vault.item.GitHub"].waitForExistence(timeout: 10), "복원한 항목이 목록에 없음")
	}

	/// 앞선 테스트가 남긴 기기 기록으로 서버 없이 잠금을 푼다 (VLT-002·VLT-003)
	func test_offlineUnlock_opensDeviceVault() async throws {
		try await requireBackend(false)
		app.launch()
		let unlock = app.buttons["unlock.submit"]
		XCTAssertTrue(unlock.waitForExistence(timeout: 10), "기기 기록이 있는데 잠금 화면에서 시작하지 않음")
		type(app.secureTextFields.firstMatch, password)
		unlock.tap()
		XCTAssertTrue(app.buttons["vault.add"].waitForExistence(timeout: 30), "서버 없이 기기 볼트가 열리지 않음")
		XCTAssertTrue(app.staticTexts["오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다."].exists
			|| app.buttons["main.reconnect"].exists, "오프라인 안내나 다시 연결 카드가 없음")
	}

	// MARK: - 도우미

	private func signUpFresh(expectVault: Bool = true) {
		app.launch()
		let switchMode = app.buttons["auth.switchMode"]
		let logout = app.buttons["unlock.logout"]
		XCTAssertTrue(switchMode.waitForExistence(timeout: 10) || logout.exists)
		if logout.exists {
			logout.tap()
			XCTAssertTrue(switchMode.waitForExistence(timeout: 10))
		}
		switchMode.tap()
		type(app.textFields["auth.email"], "ios-\(UUID().uuidString.prefix(8))@example.com")
		type(app.secureTextFields.element(boundBy: 0), password)
		type(app.secureTextFields.element(boundBy: 1), password)
		app.buttons["auth.submit"].tap()
		if expectVault {
			XCTAssertTrue(app.buttons["vault.add"].waitForExistence(timeout: 30), "가입 뒤 볼트 화면이 보이지 않음")
		}
	}

	private func addItem(_ name: String) {
		app.buttons["vault.add"].tap()
		type(app.textFields["vault.form.service_name"], name)
		type(app.secureTextFields.firstMatch, "pw-\(name)-2468")
		app.buttons["vault.form.save"].tap()
		XCTAssertTrue(app.buttons["vault.edit"].waitForExistence(timeout: 15))
		app.buttons["vault.back"].tap()
		XCTAssertTrue(app.buttons["vault.item.\(name)"].waitForExistence(timeout: 5))
	}

	private func lockFromSettings() {
		app.buttons["main.tab.settings"].tap()
		let lock = app.buttons["settings.lock"]
		XCTAssertTrue(lock.waitForExistence(timeout: 5))
		lock.tap()
		XCTAssertTrue(app.buttons["unlock.submit"].waitForExistence(timeout: 10))
	}

	private func type(_ field: XCUIElement, _ text: String) {
		XCTAssertTrue(field.waitForExistence(timeout: 10), "입력 칸 없음: \(field)")
		field.tap()
		#if os(iOS)
		// 누른 뒤 포커스가 오기 전에 입력하면 이벤트를 만들지 못한다
		_ = app.keyboards.firstMatch.waitForExistence(timeout: 5)
		#endif
		field.typeText(text)
	}

	private func clear(_ field: XCUIElement) {
		field.tap()
		let current = field.value as? String ?? ""
		field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
	}

	private func wait(timeout: TimeInterval = 15, until condition: @escaping () -> Bool) -> Bool {
		let deadline = Date().addingTimeInterval(timeout)
		while Date() < deadline {
			if condition() { return true }
			RunLoop.current.run(until: Date().addingTimeInterval(0.25))
		}
		return condition()
	}

	private func requireBackend(_ running: Bool) async throws {
		var request = URLRequest(url: URL(string: "http://localhost:8080/api/v1/auth/login")!, timeoutInterval: 2)
		request.httpMethod = "POST"
		let isRunning = (try? await URLSession.shared.data(for: request)) != nil
		try XCTSkipUnless(isRunning == running, running ? "로컬 백엔드가 없음" : "로컬 백엔드가 떠 있음(오프라인 테스트)")
	}
}
