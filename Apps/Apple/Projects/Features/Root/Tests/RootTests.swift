import Testing
import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import MainFeatureInterface
import AuthFeatureInterface
import AuthFeatureTesting
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
	var clearActiveViewCallCount = 0

	func replaceActiveView(with viewControllable: ViewControllable) {
		replaceActiveViewCallCount += 1
		replacedViewControllable = viewControllable
	}

	func clearActiveView() {
		clearActiveViewCallCount += 1
		replacedViewControllable = nil
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
	var didRequestLockVaultCallCount = 0
	var authDidCompleteCallCount = 0

	func activate() {
		isActive = true
		isActiveSubject.send(true)
	}

	func deactivate() {
		isActive = false
		isActiveSubject.send(false)
	}

	func authDidComplete() {
		authDidCompleteCallCount += 1
	}

	func didUnlockVault() {
		didUnlockVaultCallCount += 1
	}

	func didLockVault() {
		didLockVaultCallCount += 1
	}

	func didRequestLockVault() {
		didRequestLockVaultCallCount += 1
	}
}

// MARK: - RootFeatureTests Suite

@Suite("RootFeature microRIBs 루트 라우팅 트리 및 윈도우 조립 단위 테스트")
@MainActor
struct RootFeatureTests {

	@Test("didBecomeActive 시 세션이 없으면 router.routeToAuth() 1회 호출 검증")
	func initialRouting_whenNoActiveSession_attachesAuthRIB() {
		// Given
		let lockManager = MockVaultLockManager()
		lockManager.stubbedHasActiveSession = false
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.didBecomeActive()

		// Then
		#expect(router.routeToAuthCallCount == 1)
		#expect(router.routeToUnlockCallCount == 0)
		#expect(router.routeToMainCallCount == 0)
	}

	@Test("AuthListener.authDidComplete() 수신 시 router.routeToMain() 호출 검증")
	func authListener_whenAuthCompleted_transitionsToMain() {
		// Given
		let lockManager = MockVaultLockManager()
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.authDidComplete()

		// Then
		#expect(router.routeToMainCallCount == 1)
	}

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
			unlockBuilder: unlockBuilder,
			authBuilder: MockAuthBuildable()
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

	// MARK: - 5-1. RootRouter routeToMain 호출 시 Unlock RIB 분리 및 뷰 정리 검증
	@Test("RootRouter.routeToMain() 호출 시 unlockRouting이 detach되고 활성 뷰가 정리된다")
	func rootRouter_routeToMain_detachesUnlockAndClearsActiveView() {
		// Given
		let unlockRouting = MockUnlockRouting()
		let unlockBuilder = MockUnlockBuilder(stubbedRouting: unlockRouting)
		let interactor = RootInteractableMock()
		let viewController = RootViewControllableSpy()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: unlockBuilder,
			authBuilder: MockAuthBuildable()
		)
		router.routeToUnlock()
		#expect(router.children.contains { $0 === unlockRouting })

		// When
		router.routeToMain()

		// Then
		#expect(!router.children.contains { $0 === unlockRouting })
		#expect(router.unlockRouting == nil)
		#expect(viewController.clearActiveViewCallCount == 1)
	}

	// MARK: - 5-2. RootRouter routeToUnlock 중복 호출 시 멱등성 검증
	@Test("RootRouter.routeToUnlock() 이미 부착된 상태에서 재호출 시 중복 빌드 및 부착을 건너뛴다")
	func rootRouter_routeToUnlock_whenAlreadyAttached_isIdempotent() {
		// Given
		let unlockRouting = MockUnlockRouting()
		let unlockBuilder = MockUnlockBuilder(stubbedRouting: unlockRouting)
		let interactor = RootInteractableMock()
		let viewController = RootViewControllableSpy()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: unlockBuilder,
			authBuilder: MockAuthBuildable()
		)
		router.routeToUnlock()

		// When
		router.routeToUnlock()

		// Then
		#expect(unlockBuilder.buildCallCount == 1)
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

	// MARK: - 7. RootRouter routeToMain 호출 시 Main RIB 부착 및 뷰 교체 검증
	@Test("RootRouter.routeToMain() 호출 시 mainBuilder.build(...) 실행 및 자식 RIB attach, 뷰 교체 검증")
	func rootRouter_routeToMain_attachesMainRoutingAndReplacesActiveView() {
		// Given
		let unlockBuilder = MockUnlockBuilder()
		let mainRouting = MockMainRouting()
		let mainBuilder = MockMainBuilder(routingToReturn: mainRouting)
		let interactor = RootInteractableMock()
		let viewController = RootViewControllableSpy()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: unlockBuilder,
			mainBuilder: mainBuilder,
			authBuilder: MockAuthBuildable()
		)

		// When
		router.routeToMain()

		// Then
		#expect(mainBuilder.buildCallCount == 1)
		#expect(router.children.contains { $0 === mainRouting })
		#expect(viewController.replaceActiveViewCallCount == 1)
		#if os(macOS)
		#expect(viewController.replacedViewControllable?.nsViewController === mainRouting.viewControllable.nsViewController)
		#elseif os(iOS)
		#expect(viewController.replacedViewControllable?.uiViewController === mainRouting.viewControllable.uiViewController)
		#endif
	}

	// MARK: - 8. RootRouter routeToMain 중복 호출 시 멱등성 검증
	@Test("RootRouter.routeToMain() 이미 부착된 상태에서 재호출 시 중복 빌드 및 부착을 건너뛴다")
	func rootRouter_routeToMain_whenAlreadyAttached_isIdempotent() {
		// Given
		let unlockBuilder = MockUnlockBuilder()
		let mainRouting = MockMainRouting()
		let mainBuilder = MockMainBuilder(routingToReturn: mainRouting)
		let interactor = RootInteractableMock()
		let viewController = RootViewControllableSpy()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: unlockBuilder,
			mainBuilder: mainBuilder,
			authBuilder: MockAuthBuildable()
		)
		router.routeToMain()

		// When
		router.routeToMain()

		// Then
		#expect(mainBuilder.buildCallCount == 1)
	}

	// MARK: - 9. RootRouter routeToUnlock 호출 시 기존 Main RIB 분리 검증
	@Test("RootRouter.routeToUnlock() 호출 시 기 부착된 mainRouting이 detach되고 자식 목록에서 제거된다")
	func rootRouter_routeToUnlock_whenMainAttached_detachesMainRouting() {
		// Given
		let unlockRouting = MockUnlockRouting()
		let unlockBuilder = MockUnlockBuilder(stubbedRouting: unlockRouting)
		let mainRouting = MockMainRouting()
		let mainBuilder = MockMainBuilder(routingToReturn: mainRouting)
		let interactor = RootInteractableMock()
		let viewController = RootViewControllableSpy()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: unlockBuilder,
			mainBuilder: mainBuilder,
			authBuilder: MockAuthBuildable()
		)
		router.attachChild(mainRouting)
		#expect(router.children.contains { $0 === mainRouting })

		// When
		router.routeToUnlock()

		// Then
		#expect(!router.children.contains { $0 === mainRouting })
		#expect(router.mainRouting == nil)
	}

	// MARK: - 10. RootInteractor MainListener didRequestLockVault 수신 시 Unlock RIB 전이 검증
	@Test("RootInteractor가 MainListener.didRequestLockVault() 수신 시 router.routeToUnlock() 호출 검증")
	func rootInteractor_didRequestLockVault_routesToUnlock() {
		// Given
		let lockManager = MockVaultLockManager()
		let interactor = RootInteractor(lockManager: lockManager)
		let router = RootRoutingMock()
		interactor.router = router

		// When
		interactor.didRequestLockVault()

		// Then
		#expect(router.routeToUnlockCallCount == 1)
	}
}
