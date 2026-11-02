import Foundation
import MicroRIBs
import PasswordGeneratorFeatureInterface

@MainActor
protocol PasswordGeneratorInteractable: Interactable {
	var router: PasswordGeneratorRouting? { get set }
	var listener: PasswordGeneratorListener? { get set }
}

@MainActor
final class PasswordGeneratorRouter: ViewableRouter<PasswordGeneratorInteractable, PasswordGeneratorViewControllable>, PasswordGeneratorRouting {
	override init(interactor: PasswordGeneratorInteractable, viewController: PasswordGeneratorViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}
}
