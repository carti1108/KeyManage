import Foundation

/// SEC-005 계정 삭제 도메인 에러
public enum AccountDeletionError: LocalizedError, Sendable, Equatable {
	case confirmationMismatch
	case invalidPassword
	case offline
	case sessionExpired
	case tooManyAttempts
	case deviceClearFailed
	case networkError(String)
	case vaultLocked

	public var errorDescription: String? {
		switch self {
		case .confirmationMismatch:
			return AccountDeletionStrings.confirmationPrompt
		case .invalidPassword:
			return AccountDeletionStrings.invalidPasswordMessage
		case .offline:
			return AccountDeletionStrings.offlineMessage
		case .sessionExpired:
			return AccountDeletionStrings.sessionExpiredMessage
		case .tooManyAttempts:
			return AccountDeletionStrings.tooManyAttemptsMessage
		case .deviceClearFailed:
			return AccountDeletionStrings.deviceClearFailedMessage
		case .networkError(let msg):
			return msg
		case .vaultLocked:
			return "볼트가 잠겨 있어 계정을 삭제할 수 없습니다."
		}
	}
}
