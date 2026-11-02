import MicroRIBs

@MainActor
public protocol MainRouting: ViewableRouting {
	func routeToVaultList()
	func routeToSettings()
}
