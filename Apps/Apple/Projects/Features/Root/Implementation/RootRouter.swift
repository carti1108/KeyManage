import Foundation
import MicroRIBs
import UnlockFeatureInterface
import MainFeatureInterface
import AuthFeatureInterface
import RootFeatureInterface

// MARK: - Interactable

@MainActor
protocol RootInteractable: Interactable, UnlockListener, MainListener, AuthListener {
	var router: RootRouting? { get set }
	var listener: RootListener? { get set }
	func didLockVault()
}

// MARK: - Router

@MainActor
final class RootRouter: ViewableRouter<RootInteractable, RootViewControllable>, RootRouting {
	let unlockBuilder: UnlockBuildable
	let mainBuilder: MainBuildable?
	let authBuilder: AuthBuildable
	private(set) var unlockRouting: UnlockRouting?
	private(set) var mainRouting: MainRouting?
	private(set) var authRouting: AuthRouting?

	init(
		interactor: RootInteractable,
		viewController: RootViewControllable,
		unlockBuilder: UnlockBuildable,
		mainBuilder: MainBuildable? = nil,
		authBuilder: AuthBuildable
	) {
		self.unlockBuilder = unlockBuilder
		self.mainBuilder = mainBuilder
		self.authBuilder = authBuilder
		super.init(interactor: interactor, viewController: viewController)
	}

	func routeToAuth() {
		guard authRouting == nil else { return }

		if let existingUnlock = self.unlockRouting {
			detachChild(existingUnlock)
			self.unlockRouting = nil
		}
		if let existingMain = self.mainRouting {
			detachChild(existingMain)
			self.mainRouting = nil
		}
		for child in children where child is UnlockRouting || child is MainRouting {
			detachChild(child)
		}

		let auth = authBuilder.build(withListener: interactor)
		self.authRouting = auth
		attachChild(auth)
		viewController.replaceActiveView(with: auth.viewControllable)
	}

	func routeToUnlock() {
		guard unlockRouting == nil else { return }

		if let existingAuth = self.authRouting {
			detachChild(existingAuth)
			self.authRouting = nil
		}
		if let existingMain = self.mainRouting {
			detachChild(existingMain)
			self.mainRouting = nil
		}
		for child in children where child is AuthRouting || child is MainRouting {
			detachChild(child)
		}

		let unlock = unlockBuilder.build(withListener: interactor)
		self.unlockRouting = unlock
		attachChild(unlock)
		viewController.replaceActiveView(with: unlock.viewControllable)
	}

	func routeToMain() {
		guard mainRouting == nil else { return }

		if let existingAuth = self.authRouting {
			detachChild(existingAuth)
			self.authRouting = nil
		}
		if let existingUnlock = self.unlockRouting {
			detachChild(existingUnlock)
			self.unlockRouting = nil
		}
		for child in children where child is AuthRouting || child is UnlockRouting {
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
