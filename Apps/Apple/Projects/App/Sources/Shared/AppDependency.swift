import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import UnlockFeatureImplementation
import RootFeatureInterface

@MainActor
final class AppDependency: RootDependency, UnlockDependency {
	let lockManager: VaultLockManagerProtocol
	lazy var unlockBuilder: UnlockBuildable = {
		UnlockBuilder(dependency: self)
	}()

	init(lockManager: VaultLockManagerProtocol = VaultLockManager()) {
		self.lockManager = lockManager
	}
}
