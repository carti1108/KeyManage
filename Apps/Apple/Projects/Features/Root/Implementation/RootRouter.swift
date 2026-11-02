import Foundation
import MicroRIBs
import UnlockFeatureInterface
import MainFeatureInterface
import RootFeatureInterface

// MARK: - Interactable

@MainActor
protocol RootInteractable: Interactable, UnlockListener, MainListener {
	var router: RootRouting? { get set }
	var listener: RootListener? { get set }
	func didLockVault()
}

// MARK: - Router

@MainActor
final class RootRouter: ViewableRouter<RootInteractable, RootViewControllable>, RootRouting {
	let unlockBuilder: UnlockBuildable
	let mainBuilder: MainBuildable?
	private(set) var unlockRouting: UnlockRouting?
	private(set) var mainRouting: MainRouting?

	init(
		interactor: RootInteractable,
		viewController: RootViewControllable,
		unlockBuilder: UnlockBuildable,
		mainBuilder: MainBuildable? = nil
	) {
		self.unlockBuilder = unlockBuilder
		self.mainBuilder = mainBuilder
		super.init(interactor: interactor, viewController: viewController)
	}

	func routeToUnlock() {
		guard unlockRouting == nil else { return }

		if let existingMain = self.mainRouting {
			detachChild(existingMain)
			self.mainRouting = nil
		}
		for child in children where child is MainRouting {
			detachChild(child)
		}

		let unlock = unlockBuilder.build(withListener: interactor)
		self.unlockRouting = unlock
		attachChild(unlock)
		viewController.replaceActiveView(with: unlock.viewControllable)
	}

	func routeToMain() {
		guard mainRouting == nil else { return }

		if let existingUnlock = self.unlockRouting {
			detachChild(existingUnlock)
			self.unlockRouting = nil
		}
		for child in children where child is UnlockRouting {
			detachChild(child)
		}

		if let mainBuilder = mainBuilder {
			let main = mainBuilder.build(withListener: interactor)
			self.mainRouting = main
			attachChild(main)
			viewController.replaceActiveView(with: main.viewControllable)
		} else {
			viewController.clearActiveView()
		}
	}
}
