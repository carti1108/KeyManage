import MicroRIBs

@MainActor
public protocol MainViewControllable: ViewControllable {
	func setVaultListViewControllable(_ viewControllable: ViewControllable)
	func setSettingsViewControllable(_ viewControllable: ViewControllable)
}
