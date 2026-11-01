import Foundation
import MicroRIBs

@MainActor
public protocol RootViewControllable: ViewControllable {
	func replaceActiveView(with viewControllable: ViewControllable)
	func clearActiveView()
}
