import Foundation
import Combine
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import MainFeatureInterface

// MARK: - MockVaultListBuilder

@MainActor
public final class MockVaultListBuilder: VaultListBuildable {
	public var buildCallCount = 0
	public var receivedListener: VaultListListener?
	public var routingToReturn: VaultListRouting

	public init(routingToReturn: VaultListRouting = MockVaultListRouting()) {
		self.routingToReturn = routingToReturn
	}

	public func build(withListener listener: VaultListListener) -> VaultListRouting {
		buildCallCount += 1
		receivedListener = listener
		return routingToReturn
	}
}

@MainActor
public final class MockVaultListRouting: VaultListRouting {
	public let viewControllable: ViewControllable
	public var interactable: Interactable
	public var children: [Routing] = []
	public var loadCallCount = 0
	public var attachChildCallCount = 0
	public var detachChildCallCount = 0

	public var lifecycle: AsyncStream<RouterLifecycle> {
		lifecycleSubject.stream
	}
	private let lifecycleSubject = PassthroughAsyncSubject<RouterLifecycle>()

	public init(
		viewControllable: ViewControllable = MockVaultListViewControllable(),
		interactable: Interactable = MockVaultListInteractable()
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
public final class MockVaultListInteractable: Interactable {
	public var isActive: Bool {
		isActiveSubject.value
	}
	public var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	public init() {}
	public func activate() { isActiveSubject.send(true) }
	public func deactivate() { isActiveSubject.send(false) }
}

#if os(macOS)
import AppKit
public typealias MainTestingPlatformVC = NSViewController
#elseif os(iOS)
import UIKit
public typealias MainTestingPlatformVC = UIViewController
#endif

@MainActor
public final class MockVaultListViewControllable: VaultListViewControllable {
#if os(macOS)
	public var nsViewController: NSViewController = NSViewController()
	public init(nsViewController: NSViewController = NSViewController()) {
		self.nsViewController = nsViewController
	}
#elseif os(iOS)
	public var uiViewController: UIViewController = UIViewController()
	public init(uiViewController: UIViewController = UIViewController()) {
		self.uiViewController = uiViewController
	}
#endif
}

// MARK: - MockVaultLockManager

public final class MockVaultLockManager: VaultLockManagerProtocol, @unchecked Sendable {
	public var isLocked: Bool = true
	public var hasActiveSession: Bool = true
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
	public var routeToVaultListCallCount = 0
	public var routeToSettingsCallCount = 0
	public var routeToPasswordGeneratorCallCount = 0

	public init(
		viewControllable: ViewControllable = MockMainViewControllable(),
		interactable: Interactable = MockMainInteractable()
	) {
		self.viewControllable = viewControllable
		self.interactable = interactable
	}

	public func routeToVaultList() {
		routeToVaultListCallCount += 1
	}

	public func routeToSettings() {
		routeToSettingsCallCount += 1
	}

	public func routeToPasswordGenerator() {
		routeToPasswordGeneratorCallCount += 1
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
	public var setVaultListCallCount = 0
	public var setSettingsCallCount = 0
	public var setPasswordGeneratorCallCount = 0

	public init(viewController: NSViewController = NSViewController()) {
		self.nsViewController = viewController
	}

	public func setVaultListViewControllable(_ viewControllable: ViewControllable) {
		setVaultListCallCount += 1
	}

	public func setSettingsViewControllable(_ viewControllable: ViewControllable) {
		setSettingsCallCount += 1
	}

	public func setPasswordGeneratorViewControllable(_ viewControllable: ViewControllable) {
		setPasswordGeneratorCallCount += 1
	}
}
#elseif os(iOS)
import UIKit

@MainActor
public final class MockMainViewControllable: MainViewControllable {
	public var uiViewController: UIViewController
	public var setVaultListCallCount = 0
	public var setSettingsCallCount = 0
	public var setPasswordGeneratorCallCount = 0

	public init(viewController: UIViewController = UIViewController()) {
		self.uiViewController = viewController
	}

	public func setVaultListViewControllable(_ viewControllable: ViewControllable) {
		setVaultListCallCount += 1
	}

	public func setSettingsViewControllable(_ viewControllable: ViewControllable) {
		setSettingsCallCount += 1
	}

	public func setPasswordGeneratorViewControllable(_ viewControllable: ViewControllable) {
		setPasswordGeneratorCallCount += 1
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
