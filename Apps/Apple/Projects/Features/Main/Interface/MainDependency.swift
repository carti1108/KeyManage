import Foundation
import MicroRIBs
import KeyManageDomain
import VaultListFeatureInterface
import SettingsFeatureInterface
import PasswordGeneratorFeatureInterface

@MainActor
public protocol MainDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var vaultListBuilder: VaultListBuildable { get }
	var settingsBuilder: SettingsBuildable { get }
	var passwordGeneratorBuilder: PasswordGeneratorBuildable { get }
}
