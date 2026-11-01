import Foundation
import XCTest
@testable import KeyManageDomain

final class ClipboardManagerTests: XCTestCase {

	// MARK: - Test Doubles

	final class MockPasteboard: PasteboardProtocol, @unchecked Sendable {
		private let lock = NSLock()
		private var _content: String?
		private var _setStringCallCount = 0
		private var _clearCallCount = 0
		private var _shouldThrowOnSetString = false
		private var _shouldThrowOnClear = false

		var content: String? {
			lock.lock()
			defer { lock.unlock() }
			return _content
		}

		var setStringCallCount: Int {
			lock.lock()
			defer { lock.unlock() }
			return _setStringCallCount
		}

		var clearCallCount: Int {
			lock.lock()
			defer { lock.unlock() }
			return _clearCallCount
		}

		var shouldThrowOnSetString: Bool {
			get {
				lock.lock()
				defer { lock.unlock() }
				return _shouldThrowOnSetString
			}
			set {
				lock.lock()
				defer { lock.unlock() }
				_shouldThrowOnSetString = newValue
			}
		}

		var shouldThrowOnClear: Bool {
			get {
				lock.lock()
				defer { lock.unlock() }
				return _shouldThrowOnClear
			}
			set {
				lock.lock()
				defer { lock.unlock() }
				_shouldThrowOnClear = newValue
			}
		}

		func setString(_ string: String) throws {
			lock.lock()
			defer { lock.unlock() }
			if _shouldThrowOnSetString {
				throw ClipboardError.copyFailed
			}
			_content = string
			_setStringCallCount += 1
		}

		func clear() throws {
			lock.lock()
			defer { lock.unlock() }
			if _shouldThrowOnClear {
				throw ClipboardError.clearFailed
			}
			_content = nil
			_clearCallCount += 1
		}

		func getString() -> String? {
			lock.lock()
			defer { lock.unlock() }
			return _content
		}
	}

	final class MockClipboardTimerScheduler: ClipboardTimerScheduler, @unchecked Sendable {
		final class ScheduledAction: @unchecked Sendable {
			let id: Int
			let interval: TimeInterval
			let action: @Sendable () -> Void
			var isCancelled: Bool = false

			init(id: Int, interval: TimeInterval, action: @escaping @Sendable () -> Void) {
				self.id = id
				self.interval = interval
				self.action = action
			}
		}

		private let lock = NSLock()
		private var _tasks: [ScheduledAction] = []
		private var _nextId = 0

		var scheduledTasks: [ScheduledAction] {
			lock.lock()
			defer { lock.unlock() }
			return _tasks
		}

		var activeTasks: [ScheduledAction] {
			lock.lock()
			defer { lock.unlock() }
			return _tasks.filter { !$0.isCancelled }
		}

		var activeTaskCount: Int {
			lock.lock()
			defer { lock.unlock() }
			return _tasks.filter { !$0.isCancelled }.count
		}

		var lastScheduledInterval: TimeInterval? {
			lock.lock()
			defer { lock.unlock() }
			return _tasks.last?.interval
		}

		func schedule(
			after interval: TimeInterval,
			action: @escaping @Sendable () -> Void
		) -> (@Sendable () -> Void) {
			lock.lock()
			defer { lock.unlock() }
			let id = _nextId
			_nextId += 1
			let task = ScheduledAction(id: id, interval: interval, action: action)
			_tasks.append(task)

			return { [weak self, weak task] in
				self?.lock.lock()
				task?.isCancelled = true
				self?.lock.unlock()
			}
		}

		func fireAllActive() {
			let actions: [@Sendable () -> Void]
			lock.lock()
			actions = _tasks.filter { !$0.isCancelled }.map { $0.action }
			lock.unlock()

			for action in actions {
				action()
			}
		}

		func fireLast() {
			let action: (@Sendable () -> Void)?
			lock.lock()
			action = _tasks.last(where: { !$0.isCancelled })?.action
			lock.unlock()

			action?()
		}
	}

	// MARK: - Properties

	private var pasteboard: MockPasteboard!
	private var scheduler: MockClipboardTimerScheduler!
	private var sut: ClipboardManager!

	override func setUp() {
		super.setUp()
		pasteboard = MockPasteboard()
		scheduler = MockClipboardTimerScheduler()
		sut = ClipboardManager(pasteboard: pasteboard, scheduler: scheduler)
	}

	override func tearDown() {
		sut = nil
		scheduler = nil
		pasteboard = nil
		super.tearDown()
	}

	// MARK: - 1. COM-001 사용자 문구 및 에러 정책 검증

	func test_clipboardStrings_matchCOM001Spec() {
		XCTAssertEqual(ClipboardStrings.copySuccessTitle, "클립보드 보안 소각")
		XCTAssertEqual(
			ClipboardStrings.copySuccessMessage,
			"보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다."
		)
		XCTAssertEqual(ClipboardStrings.copyFailedMessage, "클립보드에 복사하지 못했습니다.")
		XCTAssertEqual(ClipboardError.copyFailed.errorDescription, "클립보드에 복사하지 못했습니다.")
		XCTAssertEqual(ClipboardError.clearFailed.errorDescription, "클립보드를 비우지 못했습니다.")
	}

	// MARK: - 2. 기본 유효 시간(30초) 및 초기 상태 검증

	func test_defaultAutoBurnInterval_is30Seconds() {
		XCTAssertEqual(ClipboardManager.defaultAutoBurnInterval, 30.0)
		XCTAssertEqual(sut.autoBurnInterval, 30.0)
		XCTAssertFalse(sut.isTimerActive)
	}

	// MARK: - 3. 복사 시 30초 타이머 스케줄링 및 30초 후 자동 소각 검증

	func test_copy_setsStringToPasteboardAndSchedules30SecondTimer() throws {
		let testValue = "my-secret-vault-password"

		try sut.copy(testValue)

		// 클립보드에 값이 정상 복사되었는지 검증
		XCTAssertEqual(pasteboard.getString(), testValue, "복사 시 시스템 클립보드에 해당 값이 설정되어야 합니다.")
		XCTAssertEqual(pasteboard.setStringCallCount, 1)

		// 30초 타이머가 활성화되었는지 검증
		XCTAssertTrue(sut.isTimerActive, "복사 완료 후 소각 타이머가 활성화되어야 합니다.")
		XCTAssertEqual(scheduler.activeTaskCount, 1)
		XCTAssertEqual(scheduler.lastScheduledInterval, 30.0, "소각 타이머는 정확히 30.0초로 스케줄링되어야 합니다.")

		// 30초 경과 시점 시뮬레이션
		scheduler.fireAllActive()

		// 30초 후 클립보드가 비워졌는지 검증
		XCTAssertNil(pasteboard.getString(), "30초가 지나면 클립보드 내용이 비워져야 합니다.")
		XCTAssertEqual(pasteboard.clearCallCount, 1, "30초 경과 시 clear()가 1회 호출되어야 합니다.")
		XCTAssertFalse(sut.isTimerActive, "소각 후 타이머는 비활성화되어야 합니다.")
	}

	// MARK: - 4. 30초 이전 다른 텍스트 복사 시 기존 타이머 취소 및 새 30초 타이머 교체 검증

	func test_copy_whenCalledAgainBefore30Seconds_cancelsPreviousTimerAndResetsTimer() throws {
		try sut.copy("password-alpha")
		XCTAssertEqual(scheduler.scheduledTasks.count, 1)
		XCTAssertEqual(pasteboard.getString(), "password-alpha")

		// 30초가 지나기 전 두 번째 텍스트 복사
		try sut.copy("password-beta")

		// 기존 첫 번째 타이머는 취소되고 새 타이머가 등록되었는지 검증
		XCTAssertEqual(scheduler.scheduledTasks.count, 2)
		guard scheduler.scheduledTasks.count >= 2 else { return }
		XCTAssertTrue(scheduler.scheduledTasks[0].isCancelled, "이전 소각 타이머는 취소되어야 합니다.")
		XCTAssertFalse(scheduler.scheduledTasks[1].isCancelled, "새 소각 타이머는 유효해야 합니다.")
		XCTAssertEqual(scheduler.activeTaskCount, 1)
		XCTAssertEqual(pasteboard.getString(), "password-beta")

		// 새 타이머 만료 시뮬레이션
		scheduler.fireLast()

		XCTAssertNil(pasteboard.getString(), "새 타이머 만료 시 클립보드가 비워져야 합니다.")
		XCTAssertEqual(pasteboard.clearCallCount, 1)
		XCTAssertFalse(sut.isTimerActive)
	}

	// MARK: - 5. 이전 타이머 지연 실행 시 새 복사 내용 보존 검증 (Generation Safety)

	func test_copy_staleTimerDoesNotClearNewContent() throws {
		try sut.copy("first-password")
		let firstTask = scheduler.scheduledTasks.first

		try sut.copy("second-password")

		// 만에 하나 이전 타이머 클로저가 지연 실행되더라도 최신 내용을 삭제해서는 안 됨
		firstTask?.action()

		XCTAssertEqual(pasteboard.getString(), "second-password", "이전 타이머 실행이 최신 복사 내용을 덮어써서는 안 됩니다.")
		XCTAssertTrue(sut.isTimerActive)
	}

	// MARK: - 6. 앱 잠금 시(handleLock) 30초 대기 없이 즉시 소각 및 타이머 취소 검증 (COM-001, SEC-003)

	func test_handleLock_clearsClipboardImmediatelyAndCancelsActiveTimer() throws {
		try sut.copy("confidential-api-key")
		XCTAssertTrue(sut.isTimerActive)
		XCTAssertEqual(scheduler.activeTaskCount, 1)

		// 앱 잠금 이벤트 수신
		sut.handleLock()

		// 즉시 비워짐 검증
		XCTAssertNil(pasteboard.getString(), "앱 잠금 시 즉시 클립보드가 비워져야 합니다.")
		XCTAssertEqual(pasteboard.clearCallCount, 1, "즉시 clear()가 호출되어야 합니다.")
		XCTAssertFalse(sut.isTimerActive, "잠금 후 소각 타이머는 비활성화되어야 합니다.")
		XCTAssertEqual(scheduler.activeTaskCount, 0, "진행 중인 스케줄러 태스크는 취소되어야 합니다.")

		// 이미 취소된 타이머가 실행되어도 추가 부작용 없어야 함
		scheduler.fireAllActive()
		XCTAssertEqual(pasteboard.clearCallCount, 1)
	}

	// MARK: - 7. 아이들 상태에서 handleLock 호출 시 안전성 검증

	func test_handleLock_whenIdle_doesNotThrowOrCorruptState() {
		XCTAssertFalse(sut.isTimerActive)
		sut.handleLock()
		XCTAssertFalse(sut.isTimerActive)
		XCTAssertNil(pasteboard.getString())
	}

	// MARK: - 8. 복사 실패 시 예외 전파 및 타이머 미등록 검증

	func test_copy_whenPasteboardThrows_propagatesErrorAndDoesNotScheduleTimer() {
		pasteboard.shouldThrowOnSetString = true

		XCTAssertThrowsError(try sut.copy("should-fail-password")) { error in
			guard let clipboardError = error as? ClipboardError else {
				XCTFail("ClipboardError 타입이어야 합니다: \(error)")
				return
			}
			XCTAssertEqual(clipboardError, .copyFailed)
			XCTAssertEqual(clipboardError.errorDescription, ClipboardStrings.copyFailedMessage)
		}

		XCTAssertFalse(sut.isTimerActive, "복사 실패 시 타이머가 활성화되어서는 안 됩니다.")
		XCTAssertEqual(scheduler.activeTaskCount, 0, "복사 실패 시 어떤 태스크도 스케줄링되어서는 안 됩니다.")
		XCTAssertNil(pasteboard.getString())
	}

	// MARK: - 9. 커스텀 autoBurnInterval 주입 검증

	func test_customAutoBurnInterval_schedulesWithSpecifiedDuration() throws {
		let customSut = ClipboardManager(
			pasteboard: pasteboard,
			autoBurnInterval: 15.0,
			scheduler: scheduler
		)

		XCTAssertEqual(customSut.autoBurnInterval, 15.0)

		try customSut.copy("test-custom-duration")
		XCTAssertEqual(scheduler.lastScheduledInterval, 15.0, "주입된 커스텀 인터벌로 스케줄링되어야 합니다.")
	}

	// MARK: - 10. clear() 명시적 호출 검증

	func test_clear_directlyClearsPasteboardAndCancelsTimer() throws {
		try sut.copy("temp-secret")
		XCTAssertTrue(sut.isTimerActive)

		sut.clear()

		XCTAssertNil(pasteboard.getString())
		XCTAssertEqual(pasteboard.clearCallCount, 1)
		XCTAssertFalse(sut.isTimerActive)
		XCTAssertEqual(scheduler.activeTaskCount, 0)
	}

	// MARK: - 11. VaultLockManager 잠금 연동 통합 검증 (SEC-003 & COM-001)

	func test_vaultLockManager_lockTriggersClipboardHandleLock() throws {
		let lockManager = VaultLockManager()
		lockManager.onClipboardClearNeeded = { [weak sut] in
			sut?.handleLock()
		}

		try sut.copy("password-under-vault-lock")
		XCTAssertEqual(pasteboard.getString(), "password-under-vault-lock")
		XCTAssertTrue(sut.isTimerActive)

		// VaultLockManager 잠금 트리거
		lockManager.lock()

		// COM-001: 앱이 잠기면 30초를 기다리지 않고 즉시 비운다
		XCTAssertNil(pasteboard.getString(), "VaultLockManager.lock() 호출 시 클립보드가 즉시 비워져야 합니다.")
		XCTAssertEqual(pasteboard.clearCallCount, 1)
		XCTAssertFalse(sut.isTimerActive)
	}
}
