import Foundation
import MicroRIBs
import KeyManageDomain

@MainActor
public protocol SettingsDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var networkClient: AuthNetworkClientProtocol { get }
}

public extension SettingsDependency {
	var networkClient: AuthNetworkClientProtocol {
		URLSessionAuthNetworkClient()
	}
}
