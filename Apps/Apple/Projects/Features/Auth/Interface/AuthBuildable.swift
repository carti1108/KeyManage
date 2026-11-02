import Foundation
import MicroRIBs

/// The buildable interface for AuthFeature.
@MainActor
public protocol AuthBuildable: Buildable {
	func build(withListener listener: AuthListener) -> AuthRouting
}
