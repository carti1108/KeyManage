import Foundation
import Testing
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import SettingsFeatureInterface
import PasswordGeneratorFeatureInterface
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

	func settingsDidRequestLogout() {}
}

@MainActor
final class MainViewControllableSpy: MainViewControllable {
#if os(macOS)
	var nsViewController: NSViewController = NSViewController()
#elseif os(iOS)
	var uiViewController: UIViewController = UIViewController()
#endif
	var setVaultListCallCount = 0
	var setSettingsCallCount = 0
	var setPasswordGeneratorCallCount = 0
	var receivedVaultListViewControllable: ViewControllable?
	var receivedSettingsViewControllable: ViewControllable?
	var receivedPasswordGeneratorViewControllable: ViewControllable?

	func setVaultListViewControllable(_ viewControllable: ViewControllable) {
		setVaultListCallCount += 1
		receivedVaultListViewControllable = viewControllable
	}

	func setSettingsViewControllable(_ viewControllable: ViewControllable) {
		setSettingsCallCount += 1
		receivedSettingsViewControllable = viewControllable
	}

	func setPasswordGeneratorViewControllable(_ viewControllable: ViewControllable) {
		setPasswordGeneratorCallCount += 1
		receivedPasswordGeneratorViewControllable = viewControllable
	}
}

@MainActor
final class MockSettingsRoutingSpy: SettingsRouting {
	let viewControllable: ViewControllable
	var interactable: Interactable
	var children: [Routing] = []

	var lifecycle: AsyncStream<RouterLifecycle> {
		lifecycleSubject.stream
	}
	private let lifecycleSubject = PassthroughAsyncSubject<RouterLifecycle>()

	init(
		viewControllable: ViewControllable = MainViewControllableSpy(),
		interactable: Interactable = MainInteractorSpy()
	) {
		self.viewControllable = viewControllable
		self.interactable = interactable
	}

	func load() {
		lifecycleSubject.send(.didLoad)
	}

	func attachChild(_ child: Routing) {
		children.append(child)
	}

	func detachChild(_ child: Routing) {
		children.removeAll { $0 === child }
	}
}

@MainActor
final class MockSettingsBuildableSpy: SettingsBuildable {
	var buildCallCount = 0
	var receivedListener: SettingsListener?
	var routingToReturn: SettingsRouting

	init(routingToReturn: SettingsRouting = MockSettingsRoutingSpy()) {
		self.routingToReturn = routingToReturn
	}

	func build(withListener listener: SettingsListener) -> SettingsRouting {
		buildCallCount += 1
		receivedListener = listener
		return routingToReturn
	}
}

@MainActor
final class MockPasswordGeneratorRoutingSpy: PasswordGeneratorRouting {
	let viewControllable: ViewControllable
	var interactable: Interactable
	var children: [Routing] = []

	var lifecycle: AsyncStream<RouterLifecycle> {
		lifecycleSubject.stream
	}
	private let lifecycleSubject = PassthroughAsyncSubject<RouterLifecycle>()

	init(
		viewControllable: ViewControllable = MainViewControllableSpy(),
		interactable: Interactable = MainInteractorSpy()
	) {
		self.viewControllable = viewControllable
		self.interactable = interactable
	}

	func load() {
		lifecycleSubject.send(.didLoad)
	}

	func attachChild(_ child: Routing) {
		children.append(child)
	}

	func detachChild(_ child: Routing) {
		children.removeAll { $0 === child }
	}
}

@MainActor
final class MockPasswordGeneratorBuildableSpy: PasswordGeneratorBuildable {
	var buildCallCount = 0
	var receivedListener: PasswordGeneratorListener?
	var routingToReturn: PasswordGeneratorRouting

	init(routingToReturn: PasswordGeneratorRouting = MockPasswordGeneratorRoutingSpy()) {
		self.routingToReturn = routingToReturn
	}

	func build(withListener listener: PasswordGeneratorListener) -> PasswordGeneratorRouting {
		buildCallCount += 1
		receivedListener = listener
		return routingToReturn
	}
}

@MainActor
final class MainDependencyMock: MainDependency {
	let lockManager: VaultLockManagerProtocol
	let vaultListBuilder: VaultListBuildable
	let settingsBuilder: SettingsBuildable
	let passwordGeneratorBuilder: PasswordGeneratorBuildable

	init(
		lockManager: VaultLockManagerProtocol = MockVaultLockManager(),
		vaultListBuilder: VaultListBuildable = MockVaultListBuilder(),
		settingsBuilder: SettingsBuildable = MockSettingsBuildableSpy(),
		passwordGeneratorBuilder: PasswordGeneratorBuildable = MockPasswordGeneratorBuildableSpy()
	) {
		self.lockManager = lockManager
		self.vaultListBuilder = vaultListBuilder
		self.settingsBuilder = settingsBuilder
		self.passwordGeneratorBuilder = passwordGeneratorBuilder
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

	// MARK: - 5. MainViewModel 단위 테스트
	@Test("MainViewModel.lockVault() 호출 시 onLockRequested 클로저 트리거 검증")
	func mainViewModel_lockVault_triggersOnLockRequested() {
		// Given
		let viewModel = MainViewModel()
		var lockCalled = false
		viewModel.onLockRequested = { lockCalled = true }

		// When
		viewModel.lockVault()

		// Then
		#expect(lockCalled)
	}

	@Test("MainViewModel.selectTab(_:) 호출 시 상태 갱신 및 onTabSelected 클로저 발행 검증")
	func mainViewModel_selectTab_updatesStateAndTriggersClosure() {
		// Given
		let viewModel = MainViewModel(selectedTab: .vault)
		var selectedTab: MainTab?
		viewModel.onTabSelected = { selectedTab = $0 }

		// When
		viewModel.selectTab(.settings)

		// Then
		#expect(viewModel.selectedTab == .settings)
		#expect(selectedTab == .settings)
	}

	// MARK: - 6. VaultList 라우팅 연동 검증 (신규 RED 테스트)
	@Test("MainRouter.routeToVaultList() 호출 시 vaultListBuilder.build(withListener:) 실행 및 자식 RIB attach 검증")
	func mainRouter_routeToVaultList_attachesVaultListRouting() {
		// Given
		let interactor = MainInteractorSpy()
		let viewController = MainViewControllableSpy()
		let vaultListRouting = MockVaultListRouting()
		let vaultListBuilder = MockVaultListBuilder(routingToReturn: vaultListRouting)
		let router = MainRouter(
			interactor: interactor,
			viewController: viewController,
			vaultListBuilder: vaultListBuilder
		)

		// When
		router.routeToVaultList()

		// Then
		#expect(vaultListBuilder.buildCallCount == 1)
		#expect(router.children.contains { $0 === vaultListRouting })
		#expect(viewController.setVaultListCallCount == 1)
		#expect(viewController.receivedVaultListViewControllable === vaultListRouting.viewControllable)
	}

	// MARK: - 7. Settings 라우팅 및 로그아웃 연동 검증
	@Test("MainRouter.routeToSettings() 호출 시 settingsBuilder.build(withListener:) 실행 및 자식 RIB attach, 뷰컨트롤러 마운트 검증")
	func mainRouter_routeToSettings_attachesSettingsRouting() {
		// Given
		let interactor = MainInteractorSpy()
		let viewController = MainViewControllableSpy()
		let settingsRouting = MockSettingsRoutingSpy()
		let settingsBuilder = MockSettingsBuildableSpy(routingToReturn: settingsRouting)
		let router = MainRouter(
			interactor: interactor,
			viewController: viewController,
			settingsBuilder: settingsBuilder
		)

		// When
		router.routeToSettings()

		// Then
		#expect(settingsBuilder.buildCallCount == 1)
		#expect(router.children.contains { $0 === settingsRouting })
		#expect(viewController.setSettingsCallCount == 1)
		#expect(viewController.receivedSettingsViewControllable === settingsRouting.viewControllable)
	}

	@Test("interactor.settingsDidRequestLogout() 호출 시 listener.didRequestLockVault() 1회 호출 검증")
	func mainInteractor_settingsDidRequestLogout_notifiesListener() {
		// Given
		let lockManager = MockVaultLockManager()
		let presenter = MainPresenterSpy()
		let interactor = MainInteractor(lockManager: lockManager, presenter: presenter)
		let listener = MockMainListener()
		interactor.listener = listener

		// When
		interactor.settingsDidRequestLogout()

		// Then
		#expect(listener.didRequestLockVaultCallCount == 1)
	}

	// MARK: - 8. PasswordGenerator 라우팅 검증
	@Test("MainRouter.routeToPasswordGenerator() 호출 시 passwordGeneratorBuilder.build(withListener:) 실행 및 자식 RIB attach, 뷰컨트롤러 마운트 검증")
	func mainRouter_routeToPasswordGenerator_attachesPasswordGeneratorRouting() {
		// Given
		let interactor = MainInteractorSpy()
		let viewController = MainViewControllableSpy()
		let generatorRouting = MockPasswordGeneratorRoutingSpy()
		let generatorBuilder = MockPasswordGeneratorBuildableSpy(routingToReturn: generatorRouting)
		let router = MainRouter(
			interactor: interactor,
			viewController: viewController,
			passwordGeneratorBuilder: generatorBuilder
		)

		// When
		router.routeToPasswordGenerator()

		// Then
		#expect(generatorBuilder.buildCallCount == 1)
		#expect(router.children.contains { $0 === generatorRouting })
		#expect(viewController.setPasswordGeneratorCallCount == 1)
		#expect(viewController.receivedPasswordGeneratorViewControllable === generatorRouting.viewControllable)
	}
}
