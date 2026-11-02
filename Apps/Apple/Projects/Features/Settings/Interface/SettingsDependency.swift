import Foundation
import MicroRIBs
import KeyManageDomain

@MainActor
public protocol SettingsDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
}
