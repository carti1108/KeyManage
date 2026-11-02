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
		if !lockManager.hasActiveSession {
			router?.routeToAuth()
		} else if lockManager.isLocked {
			router?.routeToUnlock()
		} else {
			router?.routeToMain()
		}
	}

	func authDidComplete() {
		router?.routeToMain()
	}

	func didUnlockVault() {
		router?.routeToMain()
	}

	func didLockVault() {
		if !lockManager.hasActiveSession {
			router?.routeToAuth()
		} else {
			router?.routeToUnlock()
		}
	}

	func didRequestLockVault() {
		if !lockManager.hasActiveSession {
			router?.routeToAuth()
		} else {
			router?.routeToUnlock()
		}
	}
}
