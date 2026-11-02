import Foundation

public enum AuthError: Error, LocalizedError, Sendable, Equatable {
	case invalidEmail
	case passwordTooShort
	case confirmationMismatch
	case invalidCharacter
	case loginFailed
	case sessionExpired
	case emailAlreadyRegistered
	case registrationDisabled
	case tooManyAttempts
	case offline
	case networkError(String)
	case other(String)

	public var errorDescription: String? {
		switch self {
		case .invalidEmail:
			return AuthStrings.invalidEmailFormatMessage
		case .passwordTooShort:
			return AuthStrings.passwordTooShortMessage
		case .confirmationMismatch:
			return AuthStrings.confirmationMismatchMessage
		case .invalidCharacter:
			return AuthStrings.invalidCharacterMessage
		case .loginFailed:
			return AuthStrings.loginFailedMessage
		case .sessionExpired:
			return AuthStrings.sessionExpiredMessage
		case .emailAlreadyRegistered:
			return AuthStrings.emailAlreadyRegisteredMessage
		case .registrationDisabled:
			return AuthStrings.registrationDisabledMessage
		case .tooManyAttempts:
			return AuthStrings.tooManyAttemptsMessage
		case .offline:
			return AuthStrings.serverConnectionFailedMessage
		case .networkError(let msg):
			return msg
		case .other(let msg):
			return msg
		}
	}
}
