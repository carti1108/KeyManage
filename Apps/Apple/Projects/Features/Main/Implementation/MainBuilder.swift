import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import SettingsFeatureInterface
import PasswordGeneratorFeatureInterface
import MainFeatureInterface

// MARK: - Component

@MainActor
final class MainComponent: Component<MainDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
	var vaultListBuilder: VaultListBuildable {
		dependency.vaultListBuilder
	}
	var settingsBuilder: SettingsBuildable {
		dependency.settingsBuilder
	}
	var passwordGeneratorBuilder: PasswordGeneratorBuildable {
		dependency.passwordGeneratorBuilder
	}
}

// MARK: - Builder

@MainActor
public final class MainBuilder: Builder<MainDependency>, MainBuildable {
	public override init(dependency: MainDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: MainListener) -> MainRouting {
		let component = MainComponent(dependency: dependency)
		let viewController = MainViewController()
		let interactor = MainInteractor(
			lockManager: component.lockManager,
			presenter: viewController
		)
		viewController.listener = interactor
		interactor.listener = listener
		let router = MainRouter(
			interactor: interactor,
			viewController: viewController,
			vaultListBuilder: component.vaultListBuilder,
			settingsBuilder: component.settingsBuilder,
			passwordGeneratorBuilder: component.passwordGeneratorBuilder
		)
		return router
	}
}
