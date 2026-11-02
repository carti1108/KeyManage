import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import UnlockFeatureImplementation
import MainFeatureInterface
import MainFeatureImplementation
import VaultListFeatureInterface
import VaultListFeatureImplementation
import RootFeatureInterface

@MainActor
final class AppDependency: RootDependency, UnlockDependency, MainDependency, VaultListDependency {
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

	init(
		lockManager: VaultLockManagerProtocol = VaultLockManager(),
		clipboardManager: ClipboardManagerProtocol = ClipboardManager(pasteboard: SystemPasteboard())
	) {
		self.lockManager = lockManager
		self.clipboardManager = clipboardManager
	}
}
