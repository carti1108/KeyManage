import Foundation
import Combine
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import MainFeatureInterface
import AuthFeatureInterface
import AuthFeatureTesting
import RootFeatureInterface

// MARK: - MockVaultLockManager

public final class MockVaultLockManager: VaultLockManagerProtocol, @unchecked Sendable {
	public var stubbedHasActiveSession: Bool = true
	public var hasActiveSession: Bool {
		stubbedHasActiveSession
	}
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
	public var hasPendingSync: Bool = false
	public var isOffline: Bool = false
	public var isSyncing: Bool = false

	public var onClipboardClearNeeded: (@Sendable () -> Void)? = nil

	// Spies / Call tracking
	public var setupSessionCallCount = 0
	public var recordInteractionCallCount = 0
	public var checkIdleTimeoutCallCount = 0
	public var didEnterBackgroundCallCount = 0
	public var willEnterForegroundCallCount = 0
	public var lockCallCount = 0
	public var logoutCallCount = 0

	public var unlockWithMasterPasswordCallCount = 0
	public var unlockWithMasterPasswordReceivedPassword: String?
	public var unlockWithMasterPasswordReceivedNow: Date?
	public var unlockWithMasterPasswordError: Error?

	public var unlockWithBiometricsCallCount = 0
	public var unlockWithBiometricsReceivedHash: Data?
	public var unlockWithBiometricsReceivedKey: Data?
	public var unlockWithBiometricsReceivedNow: Date?
	public var unlockWithBiometricsError: Error?

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
		return false
	}

	public func sync(
		networkClient: AuthNetworkClientProtocol,
		now: Date
	) async throws -> (vault: VaultData, revision: Int) {
		return (decryptedVault ?? VaultData(updatedAt: "", items: []), revision)
	}

	public func setOffline(_ isOffline: Bool) {
		self.isOffline = isOffline
	}
}

// MARK: - MockUnlockRouting

@MainActor
public final class MockUnlockRouting: UnlockRouting {
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

// MARK: - MockUnlockBuilder

@MainActor
public final class MockUnlockBuilder: UnlockBuildable {
	public var buildCallCount = 0
	public var buildReceivedListener: UnlockListener?
	public var stubbedRouting: UnlockRouting?

	public init(stubbedRouting: UnlockRouting? = nil) {
		self.stubbedRouting = stubbedRouting
	}

	public func build(withListener listener: UnlockListener) -> UnlockRouting {
		buildCallCount += 1
		buildReceivedListener = listener
		return stubbedRouting ?? MockUnlockRouting()
	}
}

// MARK: - RootListenerMock

@MainActor
public final class RootListenerMock: RootListener {
	public var didLogoutCallCount = 0
	public var didTerminateSessionCallCount = 0

	public init() {}

	public func didLogout() {
		didLogoutCallCount += 1
	}

	public func didTerminateSession() {
		didTerminateSessionCallCount += 1
	}
}

// MARK: - RootRoutingMock

@MainActor
public final class RootRoutingMock: RootRouting {
	public var routeToAuthCallCount = 0
	public var routeToUnlockCallCount = 0
	public var routeToMainCallCount = 0

	public var viewControllable: ViewControllable
	public var interactable: Interactable
	public var children: [Routing] = []
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

	public func routeToAuth() {
		routeToAuthCallCount += 1
	}

	public func routeToUnlock() {
		routeToUnlockCallCount += 1
	}

	public func routeToMain() {
		routeToMainCallCount += 1
	}

	public func load() {
		lifecycleSubject.send(.didLoad)
	}

	public func attachChild(_ child: Routing) {
		children.append(child)
	}

	public func detachChild(_ child: Routing) {
		children.removeAll { $0 === child }
	}
}

// MARK: - MockMainBuilder

@MainActor
public final class MockMainBuilder: MainBuildable {
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
	public var routeToVaultListCallCount = 0

	public init(
		viewControllable: ViewControllable = InternalMockViewControllable(),
		interactable: Interactable = InternalMockInteractable()
	) {
		self.viewControllable = viewControllable
		self.interactable = interactable
	}

	public func routeToVaultList() {
		routeToVaultListCallCount += 1
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

// MARK: - RootDependencyMock

@MainActor
public final class RootDependencyMock: RootDependency {
	public var lockManager: VaultLockManagerProtocol
	public var unlockBuilder: UnlockBuildable
	public var mainBuilder: MainBuildable
	public var authBuilder: AuthBuildable

	public init(
		lockManager: VaultLockManagerProtocol = MockVaultLockManager(),
		unlockBuilder: UnlockBuildable = MockUnlockBuilder(),
		mainBuilder: MainBuildable = MockMainBuilder(),
		authBuilder: AuthBuildable = MockAuthBuildable()
	) {
		self.lockManager = lockManager
		self.unlockBuilder = unlockBuilder
		self.mainBuilder = mainBuilder
		self.authBuilder = authBuilder
	}
}

// MARK: - Internal Mocks

#if os(macOS)
import AppKit

@MainActor
public final class InternalMockViewControllable: ViewControllable {
	public var nsViewController: NSViewController = NSViewController()
	public init() {}
}
#elseif os(iOS)
import UIKit

@MainActor
public final class InternalMockViewControllable: ViewControllable {
	public var uiViewController: UIViewController = UIViewController()
	public init() {}
}
#endif

@MainActor
public final class InternalMockInteractable: Interactable {
	public var isActive: Bool {
		isActiveSubject.value
	}
	public var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	public init() {}

	public func activate() {
		isActiveSubject.send(true)
	}

	public func deactivate() {
		isActiveSubject.send(false)
	}
}
