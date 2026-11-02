import Foundation
import MicroRIBs
import AuthFeatureInterface

@MainActor
public final class AuthBuilder: Builder<AuthDependency>, AuthBuildable {
	public override init(dependency: AuthDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: AuthListener) -> AuthRouting {
		let viewController = AuthViewController()
		let interactor = AuthInteractor(
			lockManager: dependency.lockManager,
			networkClient: dependency.networkClient,
			presenter: viewController
		)
		interactor.listener = listener
		viewController.listener = interactor
		return AuthRouter(interactor: interactor, viewController: viewController)
	}
}
