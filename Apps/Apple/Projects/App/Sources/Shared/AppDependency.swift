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
import RootFeatureInterface

@MainActor
final class AppDependency: RootDependency, UnlockDependency, MainDependency, VaultListDependency, SettingsDependency {
	let lockManager: VaultLockManagerProtocol
	let clipboardManager: ClipboardManagerProtocol

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

	init(
		lockManager: VaultLockManagerProtocol = VaultLockManager(),
		clipboardManager: ClipboardManagerProtocol = ClipboardManager(pasteboard: SystemPasteboard())
	) {
		self.lockManager = lockManager
		self.clipboardManager = clipboardManager
	}
}
