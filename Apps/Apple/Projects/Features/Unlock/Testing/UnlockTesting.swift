import Foundation
import MicroRIBs
import UnlockFeatureInterface

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
