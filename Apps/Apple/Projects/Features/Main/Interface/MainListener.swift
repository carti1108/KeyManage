import Foundation

@MainActor
public protocol MainListener: AnyObject {
	func didRequestLockVault()
}
