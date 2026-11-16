import Foundation

public enum AuthStrings {
	public static let loginFailedMessage = "이메일 또는 마스터 비밀번호가 올바르지 않습니다."
	public static let sessionExpiredMessage = "로그인이 만료되었습니다. 다시 로그인해 주세요."
	public static let tooManyAttemptsMessage = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."
	public static let emailAlreadyRegisteredMessage = "이미 가입된 이메일입니다."
	public static let registrationDisabledMessage = "현재 새 계정을 만들 수 없습니다."
	public static let passwordTooShortMessage = "마스터 비밀번호는 12자 이상이어야 합니다."
	public static let confirmationMismatchMessage = "비밀번호 확인이 일치하지 않습니다."
	public static let weakMasterPasswordWarning = "마스터 비밀번호가 보안에 취약합니다. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요."
	public static let invalidEmailFormatMessage = "올바른 이메일 주소를 입력해 주세요."
	public static let invalidCharacterMessage = "마스터 비밀번호에 쓸 수 없는 문자가 있습니다."
	public static let serverConnectionFailedMessage = "서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요."

	// UI 라벨 (iPhone 목업, docs/design-system.md 5.2)
	public static let loginTitle = "볼트에 로그인"
	public static let registerTitle = "계정 만들기"
	public static let loginButtonTitle = "로그인"
	public static let registerButtonTitle = "가입"
	public static let emailLabel = "이메일"
	public static let passwordLabel = "마스터 비밀번호"
	public static let confirmationLabel = "마스터 비밀번호 확인"
	public static let setupNotice = "볼트를 열기 위한 마스터 비밀번호를 입력해주세요. 마스터 비밀번호를 잃어버리면 복구할 수 없습니다."
	public static let switchToRegisterPrompt = "새 계정 만들기"
	public static let switchToLoginPrompt = "이미 계정이 있어요"
}
