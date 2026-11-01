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

/// The listener protocol for RootFeature.
@MainActor
public protocol RootListener: AnyObject {
	func didLogout()
	func didTerminateSession()
}

public extension RootListener {
	func didLogout() {}
	func didTerminateSession() {}
}

/// The routing interface for RootFeature.
@MainActor
public protocol RootRouting: ViewableRouting {
	func routeToUnlock()
	func routeToMain()
}

/// The buildable interface for RootFeature.
@MainActor
public protocol RootBuildable: Buildable {
	func build(withListener listener: RootListener?) -> RootRouting
}
