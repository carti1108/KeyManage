import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import SettingsFeatureInterface

@MainActor
public protocol MainDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var vaultListBuilder: VaultListBuildable { get }
	var settingsBuilder: SettingsBuildable { get }
}
