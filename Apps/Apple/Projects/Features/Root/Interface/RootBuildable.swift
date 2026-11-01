import Foundation
import MicroRIBs

/// The buildable interface for RootFeature.
@MainActor
public protocol RootBuildable: Buildable {
	func build(withListener listener: RootListener?) -> RootRouting
}
