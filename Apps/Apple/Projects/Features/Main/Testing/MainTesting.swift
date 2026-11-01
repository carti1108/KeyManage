import Foundation
import Combine
import MicroRIBs
import KeyManageDomain
import MainFeatureInterface

// MARK: - MockVaultLockManager

public final class MockVaultLockManager: VaultLockManagerProtocol, @unchecked Sendable {
	public var isLocked: Bool = true
	public var isPrivacyCoverActive: Bool = false
	public var autoLockTimeout: AutoLockTimeout = .fiveMinutes
	public var lastInteractionTime: Date = Date()

	public var email: String? = nil
	public var sessionToken: String? = nil
	public var salt: Data? = nil
	public var iterations: Int = 100_000
	public var encryptedVault: EncryptedVaultPayload? = nil

	public var masterKey: Data? = nil
	public var encryptionKey: Data? = nil
	public var decryptedVault: VaultData? = nil

	public var isBiometricsEnabled: Bool = false
	public var lastMasterPasswordUnlockDate: Date? = nil
	public var currentBiometryStateHash: Data? = nil

	public var onClipboardClearNeeded: (@Sendable () -> Void)? = nil

	public var setupSessionCallCount = 0
	public var recordInteractionCallCount = 0
	public var checkIdleTimeoutCallCount = 0
	public var didEnterBackgroundCallCount = 0
	public var willEnterForegroundCallCount = 0
	public var lockCallCount = 0
	public var logoutCallCount = 0

	public init(isLocked: Bool = true) {
		self.isLocked = isLocked
	}

	public func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool = false,
		biometryStateHash: Data? = nil
	) {
		setupSessionCallCount += 1
		self.email = email
		self.sessionToken = sessionToken
		self.salt = salt
		self.iterations = iterations
		self.encryptedVault = encryptedVault
		self.isBiometricsEnabled = isBiometricsEnabled
		self.currentBiometryStateHash = biometryStateHash
	}

	public func recordInteraction(at date: Date) {
		recordInteractionCallCount += 1
		lastInteractionTime = date
	}

	public func checkIdleTimeout(at date: Date) -> Bool {
		checkIdleTimeoutCallCount += 1
		return false
	}

	public func didEnterBackground(at date: Date) {
		didEnterBackgroundCallCount += 1
		isPrivacyCoverActive = true
	}

	public func willEnterForeground(at date: Date) {
		willEnterForegroundCallCount += 1
	}

	public func lock() {
		lockCallCount += 1
		isLocked = true
	}

	public func unlockWithMasterPassword(password: String, now: Date) throws {
		isLocked = false
	}

	public func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws {
		isLocked = false
	}

	public func logout() {
		logoutCallCount += 1
		isLocked = true
	}
}

// MARK: - MockMainListener

@MainActor
public final class MockMainListener: MainListener {
	public var didRequestLockVaultCallCount = 0

	public init() {}

	public func didRequestLockVault() {
		didRequestLockVaultCallCount += 1
	}
}

@MainActor
public final class MockMainRouting: MainRouting {
	public let viewControllable: ViewControllable
	public var interactable: Interactable
	public var children: [Routing] = []
	public var lifecycle: AsyncStream<RouterLifecycle> {
		lifecycleSubject.stream
	}
	private let lifecycleSubject = PassthroughAsyncSubject<RouterLifecycle>()

	public var loadCallCount = 0
	public var attachChildCallCount = 0
	public var detachChildCallCount = 0

	public init(
		viewControllable: ViewControllable = MockMainViewControllable(),
		interactable: Interactable = MockMainInteractable()
	) {
		self.viewControllable = viewControllable
		self.interactable = interactable
	}

	public func load() {
		loadCallCount += 1
		lifecycleSubject.send(.didLoad)
	}

	public func attachChild(_ child: Routing) {
		attachChildCallCount += 1
		children.append(child)
	}

	public func detachChild(_ child: Routing) {
		detachChildCallCount += 1
		children.removeAll { $0 === child }
	}
}

@MainActor
public final class MockMainInteractable: Interactable {
	public var isActive: Bool {
		isActiveSubject.value
	}
	public var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	public var activateCallCount = 0
	public var deactivateCallCount = 0

	public init() {}

	public func activate() {
		activateCallCount += 1
		isActiveSubject.send(true)
	}

	public func deactivate() {
		deactivateCallCount += 1
		isActiveSubject.send(false)
	}
}

#if os(macOS)
import AppKit

@MainActor
public final class MockMainViewControllable: MainViewControllable {
	public var nsViewController: NSViewController

	public init(viewController: NSViewController = NSViewController()) {
		self.nsViewController = viewController
	}
}
#elseif os(iOS)
import UIKit

@MainActor
public final class MockMainViewControllable: MainViewControllable {
	public var uiViewController: UIViewController

	public init(viewController: UIViewController = UIViewController()) {
		self.uiViewController = viewController
	}
}
#endif

@MainActor
public final class MockMainBuildable: MainBuildable {
	public var buildCallCount = 0
	public var receivedListener: MainListener?
	public var routingToReturn: MainRouting

	public init(routingToReturn: MainRouting = MockMainRouting()) {
		self.routingToReturn = routingToReturn
	}

	public func build(withListener listener: MainListener) -> MainRouting {
		buildCallCount += 1
		receivedListener = listener
		return routingToReturn
	}
}
