import Foundation
import MicroRIBs
import UnlockFeatureInterface

// MARK: - Interactable

@MainActor
protocol UnlockInteractable: Interactable {
	var router: UnlockRouting? { get set }
	var listener: UnlockListener? { get set }
	func unlockSuccess()
}

// MARK: - Router

@MainActor
final class UnlockRouter: ViewableRouter<UnlockInteractable, UnlockViewControllable>, UnlockRouting {
	override init(interactor: UnlockInteractable, viewController: UnlockViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
	}
}
