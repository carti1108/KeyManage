import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

// MARK: - Component

@MainActor
final class VaultListComponent: Component<VaultListDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
	var clipboardManager: ClipboardManagerProtocol {
		dependency.clipboardManager
	}
}

// MARK: - Builder

@MainActor
public final class VaultListBuilder: Builder<VaultListDependency>, VaultListBuildable {
	public override init(dependency: VaultListDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: VaultListListener) -> VaultListRouting {
		let component = VaultListComponent(dependency: dependency)
		let viewController = VaultListViewController()
		let interactor = VaultListInteractor(
			lockManager: component.lockManager,
			clipboardManager: component.clipboardManager,
			presenter: viewController
		)
		viewController.listener = interactor
		interactor.listener = listener
		let router = VaultListRouter(
			interactor: interactor,
			viewController: viewController
		)
		return router
	}
}
