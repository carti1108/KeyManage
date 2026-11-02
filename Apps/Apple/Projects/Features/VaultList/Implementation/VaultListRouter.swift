import Foundation
import MicroRIBs
import VaultListFeatureInterface

@MainActor
protocol VaultListInteractable: Interactable {
	var router: VaultListRouting? { get set }
	var listener: VaultListListener? { get set }
}

@MainActor
final class VaultListRouter: ViewableRouter<VaultListInteractable, VaultListViewControllable>, VaultListRouting {
	override init(interactor: VaultListInteractable, viewController: VaultListViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}
}
