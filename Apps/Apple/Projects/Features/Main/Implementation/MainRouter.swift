import Foundation
import MicroRIBs
import VaultListFeatureInterface
import SettingsFeatureInterface
import PasswordGeneratorFeatureInterface
import MainFeatureInterface

@MainActor
protocol MainInteractable: Interactable, VaultListListener, SettingsListener, PasswordGeneratorListener {
	var router: MainRouting? { get set }
	var listener: MainListener? { get set }
}

@MainActor
final class MainRouter: ViewableRouter<MainInteractable, MainViewControllable>, MainRouting {
	let vaultListBuilder: VaultListBuildable?
	let settingsBuilder: SettingsBuildable?
	let passwordGeneratorBuilder: PasswordGeneratorBuildable?
	private(set) var vaultListRouting: VaultListRouting?
	private(set) var settingsRouting: SettingsRouting?
	private(set) var passwordGeneratorRouting: PasswordGeneratorRouting?

	init(
		interactor: MainInteractable,
		viewController: MainViewControllable,
		vaultListBuilder: VaultListBuildable? = nil,
		settingsBuilder: SettingsBuildable? = nil,
		passwordGeneratorBuilder: PasswordGeneratorBuildable? = nil
	) {
		self.vaultListBuilder = vaultListBuilder
		self.settingsBuilder = settingsBuilder
		self.passwordGeneratorBuilder = passwordGeneratorBuilder
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}

	func routeToVaultList() {
		guard vaultListRouting == nil else { return }
		guard let vaultListBuilder = vaultListBuilder else { return }

		let vaultList = vaultListBuilder.build(withListener: interactor)
		self.vaultListRouting = vaultList
		attachChild(vaultList)
		viewController.setVaultListViewControllable(vaultList.viewControllable)
	}

	func routeToSettings() {
		guard settingsRouting == nil else { return }
		guard let settingsBuilder = settingsBuilder else { return }

		let settings = settingsBuilder.build(withListener: interactor)
		self.settingsRouting = settings
		attachChild(settings)
		viewController.setSettingsViewControllable(settings.viewControllable)
	}

	func routeToPasswordGenerator() {
		guard passwordGeneratorRouting == nil else { return }
		guard let passwordGeneratorBuilder = passwordGeneratorBuilder else { return }

		let generator = passwordGeneratorBuilder.build(withListener: interactor)
		self.passwordGeneratorRouting = generator
		attachChild(generator)
		viewController.setPasswordGeneratorViewControllable(generator.viewControllable)
	}
}
