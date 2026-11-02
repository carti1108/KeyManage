import Foundation
import MicroRIBs
import KeyManageDomain
import SettingsFeatureInterface

// MARK: - Component

@MainActor
final class SettingsComponent: Component<SettingsDependency> {
	var lockManager: VaultLockManagerProtocol {
		dependency.lockManager
	}
	var networkClient: AuthNetworkClientProtocol {
		dependency.networkClient
	}
}

// MARK: - Builder

@MainActor
public final class SettingsBuilder: Builder<SettingsDependency>, SettingsBuildable {
	public override init(dependency: SettingsDependency) {
		super.init(dependency: dependency)
	}

	public func build(withListener listener: SettingsListener) -> SettingsRouting {
		let component = SettingsComponent(dependency: dependency)
		let viewController = SettingsViewController()
		let interactor = SettingsInteractor(
			lockManager: component.lockManager,
			networkClient: component.networkClient,
			presenter: viewController
		)
		viewController.listener = interactor
		interactor.listener = listener
		let router = SettingsRouter(
			interactor: interactor,
			viewController: viewController
		)
		return router
	}
}
