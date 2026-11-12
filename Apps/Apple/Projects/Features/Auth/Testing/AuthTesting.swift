import Foundation
import MicroRIBs
import KeyManageDomain
import AuthFeatureInterface

#if os(macOS)
import AppKit
public typealias PlatformViewController = NSViewController
#elseif os(iOS)
import UIKit
public typealias PlatformViewController = UIViewController
#endif

@MainActor
public final class MockAuthListener: AuthListener {
	public var authDidCompleteCallCount: Int = 0

	public init() {}

	public func authDidComplete() {
		authDidCompleteCallCount += 1
	}
}

@MainActor
public final class MockAuthRouting: AuthRouting {
	public var viewControllable: ViewControllable
	public var interactable: Interactable
	public var children: [Routing] = []
	public var lifecycle: AsyncStream<RouterLifecycle> { AsyncStream { $0.finish() } }

	public init(
		viewControllable: ViewControllable = MockViewControllable(),
		interactable: Interactable = MockInteractable()
	) {
		self.viewControllable = viewControllable
		self.interactable = interactable
	}

	public func load() {}
	public func attachChild(_ child: Routing) { children.append(child) }
	public func detachChild(_ child: Routing) { children.removeAll { $0 === child } }
}

@MainActor
public final class MockAuthBuildable: AuthBuildable {
	public var buildCallCount: Int = 0
	public var routerToReturn: AuthRouting?

	public init(routerToReturn: AuthRouting? = nil) {
		self.routerToReturn = routerToReturn
	}

	public func build(withListener listener: AuthListener) -> AuthRouting {
		buildCallCount += 1
		return routerToReturn ?? MockAuthRouting()
	}
}

@MainActor
public final class MockViewControllable: ViewControllable {
	public init() {}

	#if os(macOS)
	public var nsViewController: PlatformViewController { PlatformViewController() }
	#else
	public var uiViewController: PlatformViewController { PlatformViewController() }
	#endif
}

@MainActor
public final class MockInteractable: Interactable {
	public var isActive: Bool = false
	public var isActiveStream: AsyncStream<Bool> { AsyncStream { $0.finish() } }

	public init() {}

	public func activate() { isActive = true }
	public func deactivate() { isActive = false }
}
