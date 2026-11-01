import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface

// MARK: - Component

@MainActor
final class UnlockComponent: Component<UnlockDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
	var biometricKeyStore: BiometricKeyStoreProtocol? {
		dependency.biometricKeyStore
	}
}

// MARK: - Builder

@MainActor
public final class UnlockBuilder: Builder<UnlockDependency>, UnlockBuildable {
	public override init(dependency: UnlockDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: UnlockListener) -> UnlockRouting {
		let component = UnlockComponent(dependency: dependency)
		let interactor = UnlockInteractor(
			lockManager: component.lockManager,
			biometricKeyStore: component.biometricKeyStore
		)
		interactor.listener = listener

		let viewController = UnlockViewController(
			isBiometricsEnabled: component.lockManager.isBiometricsEnabled,
			listener: interactor
		)
		interactor.presenter = viewController

		let router = UnlockRouter(interactor: interactor, viewController: viewController)
		interactor.router = router
		return router
	}
}
