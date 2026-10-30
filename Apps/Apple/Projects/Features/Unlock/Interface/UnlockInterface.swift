import Foundation
import MicroRIBs

/// The dependency protocol for UnlockFeature.
@MainActor
public protocol UnlockDependency: Dependency {}

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
