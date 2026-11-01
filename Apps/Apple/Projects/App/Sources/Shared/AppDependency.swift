import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import UnlockFeatureImplementation
import MainFeatureInterface
import MainFeatureImplementation
import RootFeatureInterface

@MainActor
final class AppDependency: RootDependency, UnlockDependency, MainDependency {
	let lockManager: VaultLockManagerProtocol
	var unlockBuilder: UnlockBuildable {
		UnlockBuilder(dependency: self)
	}
	var mainBuilder: MainBuildable {
		MainBuilder(dependency: self)
	}

	init(lockManager: VaultLockManagerProtocol = VaultLockManager()) {
		self.lockManager = lockManager
	}
}
