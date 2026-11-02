import Foundation
import MicroRIBs
import VaultListFeatureInterface
import MainFeatureInterface

@MainActor
protocol MainInteractable: Interactable, VaultListListener {
	var router: MainRouting? { get set }
	var listener: MainListener? { get set }
}

@MainActor
final class MainRouter: ViewableRouter<MainInteractable, MainViewControllable>, MainRouting {
	let vaultListBuilder: VaultListBuildable?
	private(set) var vaultListRouting: VaultListRouting?

	init(
		interactor: MainInteractable,
		viewController: MainViewControllable,
		vaultListBuilder: VaultListBuildable? = nil
	) {
		self.vaultListBuilder = vaultListBuilder
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}

	func routeToVaultList() {
		guard vaultListRouting == nil else { return }
		guard let vaultListBuilder = vaultListBuilder else { return }

		let vaultList = vaultListBuilder.build(withListener: interactor)
		self.vaultListRouting = vaultList
		attachChild(vaultList)
	}
}
