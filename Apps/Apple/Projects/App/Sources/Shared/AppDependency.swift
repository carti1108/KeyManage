import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import UnlockFeatureImplementation
import MainFeatureInterface
import MainFeatureImplementation
import VaultListFeatureInterface
import VaultListFeatureImplementation
import SettingsFeatureInterface
import SettingsFeatureImplementation
import PasswordGeneratorFeatureInterface
import PasswordGeneratorFeatureImplementation
import RootFeatureInterface

@MainActor
final class AppDependency: RootDependency, UnlockDependency, MainDependency, VaultListDependency, SettingsDependency, PasswordGeneratorDependency {
	let lockManager: VaultLockManagerProtocol
	let clipboardManager: ClipboardManagerProtocol
	let networkClient: AuthNetworkClientProtocol

	var unlockBuilder: UnlockBuildable {
		UnlockBuilder(dependency: self)
	}
	var mainBuilder: MainBuildable {
		MainBuilder(dependency: self)
	}
	var vaultListBuilder: VaultListBuildable {
		VaultListBuilder(dependency: self)
	}
	var settingsBuilder: SettingsBuildable {
		SettingsBuilder(dependency: self)
	}
	var passwordGeneratorBuilder: PasswordGeneratorBuildable {
		PasswordGeneratorBuilder(dependency: self)
	}

	init(
		lockManager: VaultLockManagerProtocol = VaultLockManager(),
		clipboardManager: ClipboardManagerProtocol = ClipboardManager(pasteboard: SystemPasteboard()),
		networkClient: AuthNetworkClientProtocol = URLSessionAuthNetworkClient()
	) {
		self.lockManager = lockManager
		self.clipboardManager = clipboardManager
		self.networkClient = networkClient
	}
}
