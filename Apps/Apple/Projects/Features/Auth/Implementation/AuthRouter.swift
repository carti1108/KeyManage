import Foundation
import MicroRIBs
import AuthFeatureInterface

@MainActor
final class AuthRouter: ViewableRouter<AuthInteractable, AuthViewControllable>, AuthRouting {
	override init(interactor: AuthInteractable, viewController: AuthViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}
}
