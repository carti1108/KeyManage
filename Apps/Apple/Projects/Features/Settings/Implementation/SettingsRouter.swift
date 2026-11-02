import Foundation
import MicroRIBs
import SettingsFeatureInterface

@MainActor
protocol SettingsInteractable: Interactable {
	var router: SettingsRouting? { get set }
	var listener: SettingsListener? { get set }
}

@MainActor
final class SettingsRouter: ViewableRouter<SettingsInteractable, SettingsViewControllable>, SettingsRouting {
	override init(interactor: SettingsInteractable, viewController: SettingsViewControllable) {
		super.init(interactor: interactor, viewController: viewController)
		interactor.router = self
	}
}
