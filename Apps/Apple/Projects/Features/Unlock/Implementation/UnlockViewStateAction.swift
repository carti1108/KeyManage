import Foundation

// MARK: - ViewState

struct UnlockViewState: Equatable, Sendable {
	var password: String = ""
	var errorMessage: String?
	var isLoading: Bool = false
	var isBiometricsEnabled: Bool = false
}

// MARK: - ViewAction

enum UnlockViewAction: Equatable, Sendable {
	case viewDidLoad
	case updatePassword(String)
	case unlockWithPassword(String)
	case unlockWithBiometrics
	case clearPassword
}
