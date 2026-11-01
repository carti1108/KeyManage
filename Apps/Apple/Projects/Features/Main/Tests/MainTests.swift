import Foundation
import Testing
import MicroRIBs
import KeyManageDomain
import MainFeatureInterface
@testable import MainFeatureImplementation
@testable import MainFeatureTesting

#if os(macOS)
import AppKit
#elseif os(iOS)
import UIKit
#endif

// MARK: - Test Doubles

@MainActor
final class MainPresenterSpy: MainPresentable {
	weak var listener: MainPresentableListener?
	var selectTabCallCount = 0
	var selectedTab: MainTab?

	func selectTab(_ tab: MainTab) {
		selectTabCallCount += 1
		selectedTab = tab
	}
}

@MainActor
final class MainInteractorSpy: MainInteractable {
	weak var router: MainRouting?
	weak var listener: MainListener?

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

@MainActor
final class MainViewControllableSpy: MainViewControllable {
#if os(macOS)
	var nsViewController: NSViewController = NSViewController()
#elseif os(iOS)
	var uiViewController: UIViewController = UIViewController()
#endif
}

@MainActor
final class MainDependencyMock: MainDependency {
	let lockManager: VaultLockManagerProtocol

	init(lockManager: VaultLockManagerProtocol = MockVaultLockManager()) {
		self.lockManager = lockManager
	}
}

// MARK: - MainFeatureTests Suite

@Suite("MainFeature microRIBs 핵심 동작 및 조립 단위 테스트")
@MainActor
struct MainTests {

	// MARK: - 1. 볼트 잠금 요청 시 LockManager 잠금 호출 및 리스너 알림 검증
	@Test("interactor.requestLockVault() 호출 시 lockManager.lockVault() 및 listener.didRequestLockVault() 1회 호출 검증")
	func mainInteractor_requestLockVault_callsLockManagerAndNotifiesListener() {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = MainPresenterSpy()
		let interactor = MainInteractor(lockManager: lockManager, presenter: presenter)
		let listener = MockMainListener()
		interactor.listener = listener

		// When
		interactor.requestLockVault()

		// Then
		#expect(lockManager.lockCallCount == 1)
		#expect(listener.didRequestLockVaultCallCount == 1)
	}

	// MARK: - 2. 탭 선택 시 Presenter 갱신 검증
	@Test("interactor.didSelectTab(.generator) 호출 시 presenter.selectTab(.generator) 1회 호출 검증")
	func mainInteractor_didSelectTab_updatesPresenter() {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = MainPresenterSpy()
		let interactor = MainInteractor(lockManager: lockManager, presenter: presenter)

		// When
		interactor.didSelectTab(.generator)

		// Then
		#expect(presenter.selectTabCallCount == 1)
		#expect(presenter.selectedTab == .generator)
	}

	// MARK: - 3. Router 초기화 시 Interactor 바인딩 검증
	@Test("MainRouter 생성 시 interactor.router가 self로 바인딩되는지 검증")
	func mainRouter_init_bindsInteractorRouter() {
		// Given
		let interactor = MainInteractorSpy()
		let viewController = MainViewControllableSpy()

		// When
		let router = MainRouter(interactor: interactor, viewController: viewController)

		// Then
		#expect(interactor.router === router)
	}

	// MARK: - 4. Builder DI 조립 검증
	@Test("MainBuilder.build(withListener:) 호출 시 MainRouting 및 MainViewControllable이 올바르게 조립되어 반환되는지 검증")
	func mainBuilder_build_assemblesAndReturnsRouting() {
		// Given
		let lockManager = MockVaultLockManager()
		let dependency = MainDependencyMock(lockManager: lockManager)
		let builder = MainBuilder(dependency: dependency)
		let listener = MockMainListener()

		// When
		let routing = builder.build(withListener: listener)

		// Then
		#expect(routing is MainRouter)
		#expect(routing.viewControllable is MainViewControllable)
		#expect(routing.interactable is MainInteractor)
		let interactor = routing.interactable as? MainInteractor
		#expect(interactor?.listener === listener)
		#expect(interactor?.router === routing)
		#expect(interactor?.lockManager === lockManager)
	}
}
