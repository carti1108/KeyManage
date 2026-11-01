import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface
import MainFeatureInterface

/// The dependency protocol for RootFeature.
@MainActor
public protocol RootDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var unlockBuilder: UnlockBuildable { get }
	var mainBuilder: MainBuildable { get }
}
