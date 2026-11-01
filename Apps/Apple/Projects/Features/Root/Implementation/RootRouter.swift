import Foundation
import MicroRIBs
import UnlockFeatureInterface
import RootFeatureInterface

// MARK: - Interactable

@MainActor
protocol RootInteractable: Interactable, UnlockListener {
	var router: RootRouting? { get set }
	var listener: RootListener? { get set }
	func didLockVault()
}

// MARK: - Router

@MainActor
final class RootRouter: ViewableRouter<RootInteractable, RootViewControllable>, RootRouting {
	let unlockBuilder: UnlockBuildable
	private(set) var unlockRouting: UnlockRouting?

	init(
		interactor: RootInteractable,
		viewController: RootViewControllable,
		unlockBuilder: UnlockBuildable
	) {
		self.unlockBuilder = unlockBuilder
		super.init(interactor: interactor, viewController: viewController)
	}

	func routeToUnlock() {
		guard unlockRouting == nil else { return }

		let unlock = unlockBuilder.build(withListener: interactor)
		self.unlockRouting = unlock
		attachChild(unlock)
		viewController.replaceActiveView(with: unlock.viewControllable)
	}

	func routeToMain() {
		if let existingUnlock = self.unlockRouting {
			detachChild(existingUnlock)
			self.unlockRouting = nil
			viewController.clearActiveView()
		}
	}
}
