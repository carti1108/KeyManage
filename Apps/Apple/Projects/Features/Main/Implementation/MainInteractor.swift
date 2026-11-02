import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import SettingsFeatureInterface
import PasswordGeneratorFeatureInterface
import MainFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol MainPresentable: AnyObject {
	var listener: MainPresentableListener? { get set }
	func update(state: MainViewState)
	func selectTab(_ tab: MainTab)
}

extension MainPresentable {
	func update(state: MainViewState) {
		selectTab(state.selectedTab)
	}

	func selectTab(_ tab: MainTab) {}
}

// MARK: - Interactor

@MainActor
final class MainInteractor: Interactor, MainInteractable, MainPresentableListener {
	weak var router: MainRouting?
	weak var listener: MainListener?
	weak var presenter: MainPresentable?
	let lockManager: VaultLockManagerProtocol

	private(set) var state = MainViewState()

	init(
		lockManager: VaultLockManagerProtocol,
		presenter: MainPresentable? = nil
	) {
		self.lockManager = lockManager
		self.presenter = presenter
		super.init()
		presenter?.listener = self
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		request(action: .viewDidLoad)
	}

	func request(action: MainViewAction) {
		switch action {
		case .viewDidLoad:
			router?.routeToVaultList()
			presenter?.update(state: state)
		case .lockVault:
			lockManager.lock()
			listener?.didRequestLockVault()
		case .selectTab(let tab):
			state.selectedTab = tab
			presenter?.update(state: state)
			switch tab {
			case .vault:
				router?.routeToVaultList()
			case .settings:
				router?.routeToSettings()
			case .generator:
				router?.routeToPasswordGenerator()
			}
		}
	}

	func requestLockVault() {
		request(action: .lockVault)
	}

	func didSelectTab(_ tab: MainTab) {
		request(action: .selectTab(tab))
	}
}

// MARK: - VaultListListener Conformance

extension MainInteractor: VaultListListener {}

// MARK: - SettingsListener Conformance

extension MainInteractor: SettingsListener {
	func settingsDidRequestLogout() {
		listener?.didRequestLockVault()
	}
}

// MARK: - PasswordGeneratorListener Conformance

extension MainInteractor: PasswordGeneratorListener {}
