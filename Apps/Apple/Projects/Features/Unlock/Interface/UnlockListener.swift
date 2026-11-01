import Foundation
import MicroRIBs

/// The listener protocol for UnlockFeature.
@MainActor
public protocol UnlockListener: AnyObject {
	func didUnlockVault()
}
