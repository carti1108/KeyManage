import Foundation
import MicroRIBs

/// The buildable interface for UnlockFeature.
@MainActor
public protocol UnlockBuildable: Buildable {
	func build(withListener listener: UnlockListener) -> UnlockRouting
}
