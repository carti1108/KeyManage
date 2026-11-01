import Testing
import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
@testable import RootFeatureImplementation
@testable import RootFeatureTesting
@testable import RootFeatureInterface

// MARK: - Test Spies & Mocks

#if os(macOS)
import AppKit
#elseif os(iOS)
import UIKit
#endif

@MainActor
final class RootViewControllableSpy: RootViewControllable {
#if os(macOS)
	var nsViewController: NSViewController = NSViewController()
#elseif os(iOS)
	var uiViewController: UIViewController = UIViewController()
#endif
	var replaceActiveViewCallCount = 0
	var replacedViewControllable: ViewControllable?

	func replaceActiveView(with viewControllable: ViewControllable) {
		replaceActiveViewCallCount += 1
		replacedViewControllable = viewControllable
	}
}

@MainActor
final class RootInteractableMock: RootInteractable {
	weak var router: RootRouting?
	weak var listener: RootListener?

	var isActive: Bool = false
	var isActiveStream: AsyncStream<Bool> {
		isActiveSubject.stream
	}
	private let isActiveSubject = CurrentValueAsyncStream<Bool>(false)

	var didUnlockVaultCallCount = 0
	var didLockVaultCallCount = 0

	func activate() {
		isActive = true
		isActiveSubject.send(true)
	}

	func deactivate() {
		isActive = false
		isActiveSubject.send(false)
	}

	func didUnlockVault() {
		didUnlockVaultCallCount += 1
	}

	func didLockVault() {
		didLockVaultCallCount += 1
	}
}

// MARK: - RootFeatureTests Suite

@Suite("RootFeature microRIBs 루트 라우팅 트리 및 윈도우 조립 단위 테스트")
@MainActor
struct RootFeatureTests {

	// MARK: - 1. 볼트 잠김 상태 시 최초 라우팅은 Unlock RIB 부착
	@Test("didBecomeActive 시 볼트가 잠겨 있으면 router.routeToUnlock() 1회 호출 검증")
	func initialRouting_whenVaultIsLocked_attachesUnlockRIB() {
		// Given
		let lockManager = MockVaultLockManager(isLocked: true)
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.didBecomeActive()

		// Then
		#expect(router.routeToUnlockCallCount == 1)
		#expect(router.routeToMainCallCount == 0)
	}

	// MARK: - 2. 볼트 잠금 해제 상태 시 최초 라우팅은 Main RIB 부착
	@Test("didBecomeActive 시 볼트가 잠금 해제되어 있으면 router.routeToMain() 1회 호출 검증")
	func initialRouting_whenVaultIsUnlocked_attachesMainRIB() {
		// Given
		let lockManager = MockVaultLockManager(isLocked: false)
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.didBecomeActive()

		// Then
		#expect(router.routeToMainCallCount == 1)
		#expect(router.routeToUnlockCallCount == 0)
	}

	// MARK: - 3. UnlockListener didUnlockVault 수신 시 Main RIB 전이
	@Test("UnlockListener.didUnlockVault() 수신 시 router.routeToMain() 호출 검증")
	func unlockListener_whenDidUnlockVaultReceived_transitionsFromUnlockToMain() {
		// Given
		let lockManager = MockVaultLockManager(isLocked: true)
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.didUnlockVault()

		// Then
		#expect(router.routeToMainCallCount == 1)
	}

	// MARK: - 4. 볼트 잠금 이벤트 발생 시 Unlock RIB 전이
	@Test("didLockVault() 잠금 이벤트 발생 시 router.routeToUnlock() 호출 검증")
	func lockEvent_whenVaultLocked_transitionsFromMainToUnlock() {
		// Given
		let lockManager = MockVaultLockManager(isLocked: false)
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.didLockVault()

		// Then
		#expect(router.routeToUnlockCallCount == 1)
	}

	// MARK: - 5. RootRouter routeToUnlock 호출 시 Unlock RIB 생성/부착 및 뷰 교체
	@Test("RootRouter.routeToUnlock() 호출 시 unlockBuilder.build(...) 실행 및 자식 RIB attach, 뷰 교체 검증")
	func rootRouter_routeToUnlock_attachesUnlockAndReplacesActiveView() {
		// Given
		let unlockRouting = MockUnlockRouting()
		let unlockBuilder = MockUnlockBuilder(stubbedRouting: unlockRouting)
		let interactor = RootInteractableMock()
		let viewController = RootViewControllableSpy()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: unlockBuilder
		)

		// When
		router.routeToUnlock()

		// Then
		#expect(unlockBuilder.buildCallCount == 1)
		#expect(router.children.contains { $0 === unlockRouting })
		#expect(viewController.replaceActiveViewCallCount == 1)
		#if os(macOS)
		#expect(viewController.replacedViewControllable?.nsViewController === unlockRouting.viewControllable.nsViewController)
		#elseif os(iOS)
		#expect(viewController.replacedViewControllable?.uiViewController === unlockRouting.viewControllable.uiViewController)
		#endif
	}

	// MARK: - 6. RootBuilder 의존성 바인딩 검증
	@Test("RootBuilder가 lockManager, unlockBuilder 의존성을 올바르게 바인딩하여 Router/Interactor를 생성한다")
	func rootBuilder_wiresDependenciesCorrectly() {
		// Given
		let lockManager = MockVaultLockManager()
		let unlockBuilder = MockUnlockBuilder()
		let dependency = RootDependencyMock(lockManager: lockManager, unlockBuilder: unlockBuilder)
		let builder = RootBuilder(dependency: dependency)
		let listener = RootListenerMock()

		// When
		let routing = builder.build(withListener: listener)

		// Then
		#expect(routing is RootRouter)
		#expect(routing.interactable is RootInteractor)
		let interactor = routing.interactable as? RootInteractor
		#expect(interactor?.listener === listener)
		#expect(interactor?.router === routing)
		#expect(interactor?.lockManager === lockManager)
		let router = routing as? RootRouter
		#expect(router?.unlockBuilder === unlockBuilder)
		#expect(routing.viewControllable is RootViewControllable)
	}
}
