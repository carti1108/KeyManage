import MicroRIBs

@MainActor
public protocol MainBuildable: Buildable {
	func build(withListener listener: MainListener) -> MainRouting
}
