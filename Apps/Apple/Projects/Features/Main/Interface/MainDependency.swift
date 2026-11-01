import Foundation
import MicroRIBs
import KeyManageDomain

@MainActor
public protocol MainDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
}
