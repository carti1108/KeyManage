import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

@MainActor
public final class MockVaultListListener: VaultListListener {
	public init() {}
}

#if os(macOS)
import AppKit
public typealias MockVaultListPlatformViewController = NSViewController
#elseif os(iOS)
import UIKit
public typealias MockVaultListPlatformViewController = UIViewController
#endif

@MainActor
public final class MockVaultListViewControllable: VaultListViewControllable {
#if os(macOS)
	public var nsViewController: NSViewController
	public init(viewController: NSViewController = NSViewController()) {
		self.nsViewController = viewController
	}
#elseif os(iOS)
	public var uiViewController: UIViewController
	public init(viewController: UIViewController = UIViewController()) {
		self.uiViewController = viewController
	}
#endif
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

@MainActor
public final class MockVaultListBuildable: VaultListBuildable {
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

public final class MockClipboardManager: ClipboardManagerProtocol, @unchecked Sendable {
	public var autoBurnInterval: TimeInterval = 30.0
	public var isTimerActive: Bool = false

	public var copiedTexts: [String] = []
	public var copyCallCount = 0
	public var handleLockCallCount = 0
	public var clearCallCount = 0

	public init() {}

	public func copy(_ text: String) throws {
		copyCallCount += 1
		copiedTexts.append(text)
		isTimerActive = true
	}

	public func handleLock() {
		handleLockCallCount += 1
		clear()
	}

	public func clear() {
		clearCallCount += 1
		copiedTexts.removeAll()
		isTimerActive = false
	}
}
