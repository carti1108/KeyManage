import MicroRIBs

public protocol MainBuildable: Buildable {
	func build(withListener listener: MainListener) -> MainRouting
}
