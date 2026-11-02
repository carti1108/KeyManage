import MicroRIBs

@MainActor
public protocol PasswordGeneratorBuildable: Buildable {
	func build(withListener listener: PasswordGeneratorListener) -> PasswordGeneratorRouting
}
