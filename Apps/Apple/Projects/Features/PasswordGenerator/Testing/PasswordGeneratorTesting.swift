import Foundation
import MicroRIBs
import KeyManageDomain
import PasswordGeneratorFeatureInterface

@MainActor
public final class MockPasswordGeneratorListener: PasswordGeneratorListener {
	public init() {}
}

#if os(macOS)
import AppKit
public typealias MockPasswordGeneratorPlatformViewController = NSViewController
#elseif os(iOS)
import UIKit
public typealias MockPasswordGeneratorPlatformViewController = UIViewController
#endif

@MainActor
public final class MockPasswordGeneratorViewControllable: PasswordGeneratorViewControllable {
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
public final class MockPasswordGeneratorRouting: PasswordGeneratorRouting {
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
		viewControllable: ViewControllable = MockPasswordGeneratorViewControllable(),
		interactable: Interactable = MockPasswordGeneratorInteractable()
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
public final class MockPasswordGeneratorInteractable: Interactable {
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
public final class MockPasswordGeneratorBuildable: PasswordGeneratorBuildable {
	public var buildCallCount = 0
	public var receivedListener: PasswordGeneratorListener?
	public var routingToReturn: PasswordGeneratorRouting

	public init(routingToReturn: PasswordGeneratorRouting = MockPasswordGeneratorRouting()) {
		self.routingToReturn = routingToReturn
	}

	public func build(withListener listener: PasswordGeneratorListener) -> PasswordGeneratorRouting {
		buildCallCount += 1
		receivedListener = listener
		return routingToReturn
	}
}
