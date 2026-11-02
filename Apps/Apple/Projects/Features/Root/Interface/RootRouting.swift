import Foundation
import MicroRIBs

/// The routing interface for RootFeature.
@MainActor
public protocol RootRouting: ViewableRouting {
	func routeToAuth()
	func routeToUnlock()
	func routeToMain()
}
