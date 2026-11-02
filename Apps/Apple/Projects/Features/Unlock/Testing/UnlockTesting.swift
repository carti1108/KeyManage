import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface

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
	public var revision: Int = 1

	public var onClipboardClearNeeded: (@Sendable () -> Void)? = nil

	// Spies / Call tracking
	public var setupSessionCallCount = 0
	public var recordInteractionCallCount = 0
	public var checkIdleTimeoutCallCount = 0
	public var didEnterBackgroundCallCount = 0
	public var willEnterForegroundCallCount = 0
	public var lockCallCount = 0
	public var logoutCallCount = 0
	public var checkIfPasswordChangedOnServerCallCount = 0
	public var checkIfPasswordChangedOnServerResult = false

	public var unlockWithMasterPasswordCallCount = 0
	public var unlockWithMasterPasswordReceivedPassword: String?
	public var unlockWithMasterPasswordReceivedNow: Date?
	public var unlockWithMasterPasswordError: Error?

	public var unlockWithBiometricsCallCount = 0
	public var unlockWithBiometricsReceivedHash: Data?
	public var unlockWithBiometricsReceivedKey: Data?
	public var unlockWithBiometricsReceivedNow: Date?
	public var unlockWithBiometricsError: Error?

	public init() {}

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
		unlockWithMasterPasswordCallCount += 1
		unlockWithMasterPasswordReceivedPassword = password
		unlockWithMasterPasswordReceivedNow = now
		if let error = unlockWithMasterPasswordError {
			throw error
		}
		isLocked = false
	}

	public func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws {
		unlockWithBiometricsCallCount += 1
		unlockWithBiometricsReceivedHash = evaluatedStateHash
		unlockWithBiometricsReceivedKey = recoveredEncryptionKey
		unlockWithBiometricsReceivedNow = now
		if let error = unlockWithBiometricsError {
			throw error
		}
		isLocked = false
	}

	public func logout() {
		logoutCallCount += 1
		isLocked = true
	}

	public func changeMasterPassword(
		currentPassword: String,
		newPassword: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date
	) async throws {}

	public func checkIfPasswordChangedOnServer(
		networkClient: AuthNetworkClientProtocol
	) async throws -> Bool {
		checkIfPasswordChangedOnServerCallCount += 1
		return checkIfPasswordChangedOnServerResult
	}
}

// MARK: - UnlockBuildableMock (상위 RIB 단위 테스트 필수 제공)

@MainActor
public final class UnlockBuildableMock: UnlockBuildable {
	public var buildCallCount = 0
	public var buildReceivedListener: UnlockListener?
	public var buildHandler: ((UnlockListener) -> UnlockRouting)?

	public init() {}

	public func build(withListener listener: UnlockListener) -> UnlockRouting {
		buildCallCount += 1
		buildReceivedListener = listener
		if let buildHandler {
			return buildHandler(listener)
		}
		return UnlockRoutingMock()
	}
}

// MARK: - UnlockListenerMock

@MainActor
public final class UnlockListenerMock: UnlockListener {
	public var didUnlockVaultCallCount = 0

	public init() {}

	public func didUnlockVault() {
		didUnlockVaultCallCount += 1
	}
}

// MARK: - UnlockRoutingMock

@MainActor
public final class UnlockRoutingMock: UnlockRouting {
	public var viewControllable: ViewControllable
	public var interactable: Interactable
	public var children: [Routing] = []

	public var loadCallCount = 0
	public var attachChildCallCount = 0
	public var detachChildCallCount = 0
	public var attachedChildren: [Routing] = []
	public var detachedChildren: [Routing] = []

	public var lifecycle: AsyncStream<RouterLifecycle> {
		lifecycleSubject.stream
	}
	private let lifecycleSubject = PassthroughAsyncSubject<RouterLifecycle>()

	public init(
		viewControllable: ViewControllable? = nil,
		interactable: Interactable? = nil
	) {
		self.viewControllable = viewControllable ?? InternalMockViewControllable()
		self.interactable = interactable ?? InternalMockInteractable()
	}

	public func load() {
		loadCallCount += 1
		lifecycleSubject.send(.didLoad)
	}

	public func attachChild(_ child: Routing) {
		attachChildCallCount += 1
		attachedChildren.append(child)
		children.append(child)
	}

	public func detachChild(_ child: Routing) {
		detachChildCallCount += 1
		detachedChildren.append(child)
		children.removeAll { $0 === child }
	}
}

// MARK: - MockBiometricKeyStore

public final class MockBiometricKeyStore: BiometricKeyStoreProtocol, @unchecked Sendable {
	public var evaluateAndRecoverKeyCallCount = 0
	public var stubbedHash: Data = Data([0x01, 0x02, 0x03, 0x04])
	public var stubbedKey: Data = Data(repeating: 0x42, count: 32)
	public var error: Error?

	public init() {}

	public func evaluateAndRecoverKey() throws -> (biometryHash: Data, encryptionKey: Data) {
		evaluateAndRecoverKeyCallCount += 1
		if let error = error {
			throw error
		}
		return (stubbedHash, stubbedKey)
	}
}

// MARK: - Internal Mocks (전역 심볼 충돌 방지를 위해 internal 격리)

#if os(macOS)
import AppKit

@MainActor
final class InternalMockViewControllable: ViewControllable {
	var nsViewController: NSViewController = NSViewController()
}
#elseif os(iOS)
import UIKit

@MainActor
final class InternalMockViewControllable: ViewControllable {
	var uiViewController: UIViewController = UIViewController()
}
#endif

@MainActor
final class InternalMockInteractable: Interactable {
	var isActive: Bool {
		isActiveSubject.value
	}
	var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	func activate() {
		isActiveSubject.send(true)
	}

	func deactivate() {
		isActiveSubject.send(false)
	}
}
