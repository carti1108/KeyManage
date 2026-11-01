import Foundation

/// 클립보드 사용자 안내 문구 (COM-001)
public enum ClipboardStrings {
	public static let copySuccessTitle = "클립보드 보안 소각"
	public static let copySuccessMessage = "보안을 위해 복사된 내용이 30초 후 클립보드에서 자동 삭제됩니다."
	public static let copyFailedMessage = "클립보드에 복사하지 못했습니다."
	public static let clearFailedMessage = "클립보드를 비우지 못했습니다."
}

/// 클립보드 에러 정의
public enum ClipboardError: Error, LocalizedError, Sendable, Equatable {
	case copyFailed
	case clearFailed

	public var errorDescription: String? {
		switch self {
		case .copyFailed:
			return ClipboardStrings.copyFailedMessage
		case .clearFailed:
			return ClipboardStrings.clearFailedMessage
		}
	}
}

/// 시스템 클립보드(NSPasteboard/UIPasteboard) 입출력 추상화 포트
public protocol PasteboardProtocol: AnyObject, Sendable {
	func setString(_ string: String) throws
	func clear() throws
	func getString() -> String?
}

/// 클립보드 타이머 스케줄러 프로토콜 (테스트 시 가상 시간 제어 지원)
public protocol ClipboardTimerScheduler: Sendable {
	func schedule(
		after interval: TimeInterval,
		action: @escaping @Sendable () -> Void
	) -> (@Sendable () -> Void)
}

/// 기본 GCD 기반 타이머 스케줄러
public final class DefaultClipboardTimerScheduler: ClipboardTimerScheduler, @unchecked Sendable {
	private final class WorkItemBox: @unchecked Sendable {
		let item: DispatchWorkItem
		init(_ item: DispatchWorkItem) {
			self.item = item
		}
		func cancel() {
			item.cancel()
		}
	}

	public init() {}

	public func schedule(
		after interval: TimeInterval,
		action: @escaping @Sendable () -> Void
	) -> (@Sendable () -> Void) {
		let workItem = DispatchWorkItem {
			action()
		}
		let box = WorkItemBox(workItem)
		DispatchQueue.main.asyncAfter(deadline: .now() + interval, execute: workItem)
		return {
			box.cancel()
		}
	}
}

/// 클립보드 관리자 인터페이스 (COM-001)
public protocol ClipboardManagerProtocol: AnyObject, Sendable {
	var autoBurnInterval: TimeInterval { get }
	var isTimerActive: Bool { get }

	func copy(_ text: String) throws
	func copy(text: String) throws
	func handleLock()
	func clear()
}

public extension ClipboardManagerProtocol {
	func copy(text: String) throws {
		try copy(text)
	}
}

/// 볼트 값 클립보드 자동 소각 관리자 (COM-001, SEC-003)
///
/// 사용자가 복사한 볼트 값(비밀번호, 아이디 등)을 30초 뒤 자동으로 지우며,
/// 앱이 잠기면(handleLock) 30초를 기다리지 않고 즉시 비웁니다.
public final class ClipboardManager: ClipboardManagerProtocol, @unchecked Sendable {
	public static let defaultAutoBurnInterval: TimeInterval = 30.0

	public let autoBurnInterval: TimeInterval
	private let pasteboard: PasteboardProtocol
	private let scheduler: ClipboardTimerScheduler

	private let lock = NSLock()
	private var _isTimerActive: Bool = false
	private var _currentGeneration: Int = 0
	private var _cancelTimer: (@Sendable () -> Void)?

	public var isTimerActive: Bool {
		lock.lock()
		defer { lock.unlock() }
		return _isTimerActive
	}

	public init(
		pasteboard: PasteboardProtocol,
		autoBurnInterval: TimeInterval = ClipboardManager.defaultAutoBurnInterval,
		scheduler: ClipboardTimerScheduler = DefaultClipboardTimerScheduler()
	) {
		self.pasteboard = pasteboard
		self.autoBurnInterval = autoBurnInterval
		self.scheduler = scheduler
	}

	deinit {
		_cancelTimer?()
		try? pasteboard.clear()
	}

	public func copy(_ text: String) throws {
		try pasteboard.setString(text)

		let oldCancel: (@Sendable () -> Void)?
		let generation: Int

		lock.lock()
		oldCancel = _cancelTimer
		_cancelTimer = nil
		_currentGeneration += 1
		generation = _currentGeneration
		_isTimerActive = true
		lock.unlock()

		oldCancel?()

		let cancel = scheduler.schedule(after: autoBurnInterval) { [weak self] in
			self?.handleTimerFired(generation: generation)
		}

		lock.lock()
		if _currentGeneration == generation && _isTimerActive {
			_cancelTimer = cancel
			lock.unlock()
		} else {
			lock.unlock()
			cancel()
		}
	}

	public func handleLock() {
		let oldCancel: (@Sendable () -> Void)?

		lock.lock()
		oldCancel = _cancelTimer
		_cancelTimer = nil
		_isTimerActive = false
		_currentGeneration += 1
		lock.unlock()

		oldCancel?()
		try? pasteboard.clear()
	}

	public func clear() {
		let oldCancel: (@Sendable () -> Void)?

		lock.lock()
		oldCancel = _cancelTimer
		_cancelTimer = nil
		_isTimerActive = false
		_currentGeneration += 1
		lock.unlock()

		oldCancel?()
		try? pasteboard.clear()
	}

	private func handleTimerFired(generation: Int) {
		let shouldClear: Bool

		lock.lock()
		if _currentGeneration == generation && _isTimerActive {
			_isTimerActive = false
			_cancelTimer = nil
			shouldClear = true
		} else {
			shouldClear = false
		}
		lock.unlock()

		if shouldClear {
			try? pasteboard.clear()
		}
	}
}
