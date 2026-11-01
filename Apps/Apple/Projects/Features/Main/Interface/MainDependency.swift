import Foundation
import MicroRIBs
import KeyManageDomain

public protocol MainDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
}
