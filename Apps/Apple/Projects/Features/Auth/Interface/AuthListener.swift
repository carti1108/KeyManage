import Foundation
import MicroRIBs

/// The listener protocol for AuthFeature.
@MainActor
public protocol AuthListener: AnyObject {
	func authDidComplete()
}
