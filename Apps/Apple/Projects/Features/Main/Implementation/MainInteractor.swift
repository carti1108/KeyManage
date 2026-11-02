import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import MainFeatureInterface

// MARK: - Presentable Protocols

@MainActor
protocol MainPresentable: AnyObject {
	var listener: MainPresentableListener? { get set }
	func selectTab(_ tab: MainTab)
}

// MARK: - Interactor

@MainActor
final class MainInteractor: Interactor, MainInteractable, MainPresentableListener {
	weak var router: MainRouting?
	weak var listener: MainListener?
	weak var presenter: MainPresentable?
	let lockManager: VaultLockManagerProtocol

	init(
		lockManager: VaultLockManagerProtocol,
		presenter: MainPresentable? = nil
	) {
		self.lockManager = lockManager
		self.presenter = presenter
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		router?.routeToVaultList()
	}

	func requestLockVault() {
		lockManager.lock()
		listener?.didRequestLockVault()
	}

	func didSelectTab(_ tab: MainTab) {
		presenter?.selectTab(tab)
	}
}

// MARK: - VaultListListener Conformance

extension MainInteractor: VaultListListener {}
