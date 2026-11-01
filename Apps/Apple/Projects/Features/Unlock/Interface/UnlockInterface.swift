import Foundation
import MicroRIBs
import KeyManageDomain

public typealias VaultLockError = LockError

/// The dependency protocol for UnlockFeature.
@MainActor
public protocol UnlockDependency: Dependency {
	var lockManager: VaultLockManagerProtocol { get }
}

/// The listener protocol for UnlockFeature.
@MainActor
public protocol UnlockListener: AnyObject {
	func didUnlockVault()
}

/// The routing interface for UnlockFeature.
@MainActor
public protocol UnlockRouting: ViewableRouting {}

/// The buildable interface for UnlockFeature.
@MainActor
public protocol UnlockBuildable: Buildable {
	func build(withListener listener: UnlockListener) -> UnlockRouting
}
