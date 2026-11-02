import Foundation

@MainActor
public protocol SettingsListener: AnyObject {
	func settingsDidRequestLogout()
}
