import Foundation

public enum LockError: Error, LocalizedError, Sendable, Equatable {
	case invalidMasterPassword
	case biometricsDisabled
	case biometricsExpiredRequirePassword
	case biometricsInvalidatedRequirePassword
	case sessionNotInitialized
	case emptyVaultPreparationFailed

	public var errorDescription: String? {
		switch self {
		case .invalidMasterPassword:
			return "마스터 비밀번호가 올바르지 않습니다."
		case .biometricsDisabled:
			return "생체 인증이 활성화되어 있지 않습니다."
		case .biometricsExpiredRequirePassword:
			return "보안을 위해 마스터 비밀번호를 다시 입력해 주세요."
		case .biometricsInvalidatedRequirePassword:
			return "생체 정보가 변경되었습니다. 마스터 비밀번호를 입력해 주세요."
		case .sessionNotInitialized:
			return "세션이 초기화되지 않았습니다. 다시 로그인해 주세요."
		case .emptyVaultPreparationFailed:
			return "계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요."
		}
	}
}

public enum VaultLockUIStrings {
	public static let lockedTitle = "볼트가 잠겨 있습니다"
	public static let unlockButtonTitle = "잠금 해제"
	public static let biometricUnlockButtonTitle = "생체 인증으로 잠금 해제"
	public static let invalidMasterPasswordMessage = "마스터 비밀번호가 올바르지 않습니다."
	public static let emptyVaultPreparationFailedMessage = "계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요."
}
