import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface

@MainActor
public protocol MainDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var vaultListBuilder: VaultListBuildable { get }
}
