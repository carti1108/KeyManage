import Foundation
import MicroRIBs
import MainFeatureInterface

@MainActor
protocol MainInteractable: Interactable {
	var router: MainRouting? { get set }
	var listener: MainListener? { get set }
}

@MainActor
final class MainRouter: ViewableRouter<MainInteractable, MainViewControllable>, MainRouting {
	override init(interactor: MainInteractable, viewController: MainViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}
}
