import Foundation
import MicroRIBs
import KeyManageDomain
import UnlockFeatureInterface

/// The dependency protocol for RootFeature.
@MainActor
public protocol RootDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
	var unlockBuilder: UnlockBuildable { get }
}
