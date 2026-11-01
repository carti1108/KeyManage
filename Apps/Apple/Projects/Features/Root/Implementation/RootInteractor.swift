import Foundation
import MicroRIBs
import KeyManageDomain
import RootFeatureInterface

// MARK: - Interactor

@MainActor
final class RootInteractor: Interactor, RootInteractable {
	weak var router: RootRouting?
	weak var listener: RootListener?
	let lockManager: VaultLockManagerProtocol

	init(lockManager: VaultLockManagerProtocol) {
		self.lockManager = lockManager
		super.init()
	}

	override func didBecomeActive() {
		super.didBecomeActive()
		if lockManager.isLocked {
			router?.routeToUnlock()
		} else {
			router?.routeToMain()
		}
	}

	func didUnlockVault() {
		router?.routeToMain()
	}

	func didLockVault() {
		router?.routeToUnlock()
	}
}
