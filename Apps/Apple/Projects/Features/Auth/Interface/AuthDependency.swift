import Foundation
import MicroRIBs
import KeyManageDomain

/// The dependency protocol for AuthFeature.
@MainActor
public protocol AuthDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var networkClient: AuthNetworkClientProtocol { get }
}
