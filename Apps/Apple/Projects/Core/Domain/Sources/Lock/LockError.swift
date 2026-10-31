import Foundation

public enum LockError: Error, Sendable, Equatable {
	case invalidMasterPassword
	case biometricsDisabled
	case biometricsExpiredRequirePassword
	case biometricsInvalidatedRequirePassword
	case sessionNotInitialized
	case notImplemented
}
