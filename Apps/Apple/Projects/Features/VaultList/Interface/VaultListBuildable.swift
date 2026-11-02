import MicroRIBs

@MainActor
public protocol VaultListBuildable: Buildable {
	func build(withListener listener: VaultListListener) -> VaultListRouting
}
