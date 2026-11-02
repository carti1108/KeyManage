import Foundation

public enum MasterPasswordChangeError: Error, LocalizedError, Sendable, Equatable {
	case invalidCurrentPassword
	case samePassword
	case tooShort
	case confirmationMismatch
	case offline
	case readOnlyVault
	case conflict
	case sessionExpired
	case vaultTooLarge
	case tooManyAttempts
	case networkError(String)
	case other(String)

	public var errorDescription: String? {
		switch self {
		case .invalidCurrentPassword:
			return MasterPasswordChangeStrings.invalidCurrentPasswordMessage
		case .samePassword:
			return MasterPasswordChangeStrings.samePasswordMessage
		case .tooShort:
			return MasterPasswordChangeStrings.tooShortMessage
		case .confirmationMismatch:
			return MasterPasswordChangeStrings.confirmationMismatchMessage
		case .offline:
			return MasterPasswordChangeStrings.offlineMessage
		case .readOnlyVault:
			return "읽기 전용 볼트에서는 비밀번호를 변경할 수 없습니다."
		case .conflict:
			return MasterPasswordChangeStrings.conflictMessage
		case .sessionExpired:
			return "세션이 만료되었습니다. 다시 로그인해 주세요."
		case .vaultTooLarge:
			return "볼트 크기가 5MB를 초과하여 저장할 수 없습니다."
		case .tooManyAttempts:
			return "너무 많은 시도가 발생했습니다. 잠시 후 다시 시도해 주세요."
		case .networkError(let msg):
			return msg
		case .other(let msg):
			return msg
		}
	}
}
