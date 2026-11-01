import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import RootFeatureInterface

// MARK: - Component

@MainActor
final class RootComponent: Component<RootDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
	var unlockBuilder: UnlockBuildable {
		dependency.unlockBuilder
	}
}

// MARK: - Builder

@MainActor
public final class RootBuilder: Builder<RootDependency>, RootBuildable {
	public override init(dependency: RootDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: RootListener?) -> RootRouting {
		let component = RootComponent(dependency: dependency)
		let interactor = RootInteractor(lockManager: component.lockManager)
		interactor.listener = listener

		let viewController = RootViewController()
		let router = RootRouter(
			interactor: interactor,
			viewController: viewController,
			unlockBuilder: component.unlockBuilder
		)
		interactor.router = router
		return router
	}
}
