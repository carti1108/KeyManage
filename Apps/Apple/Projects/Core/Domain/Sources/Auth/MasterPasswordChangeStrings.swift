import Foundation

public enum MasterPasswordChangeStrings {
	public static let noticeTitle = "마스터 비밀번호 변경"
	public static let noticeBody = "비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다."
	public static let changeButton = "변경"
	public static let currentPasswordPlaceholder = "현재 마스터 비밀번호"
	public static let newPasswordPlaceholder = "새 마스터 비밀번호"
	public static let confirmPasswordPlaceholder = "새 마스터 비밀번호 확인"
	public static let invalidCurrentPasswordMessage = "마스터 비밀번호가 올바르지 않습니다."
	public static let samePasswordMessage = "새 비밀번호가 지금 비밀번호와 같습니다."
	public static let tooShortMessage = "마스터 비밀번호는 12자 이상이어야 합니다."
	public static let confirmationMismatchMessage = "새 비밀번호와 확인이 일치하지 않습니다."
	public static let offlineMessage = "인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다."
	public static let conflictMessage = "다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요."
	public static let completedMessage = "마스터 비밀번호를 바꿨습니다."
	public static let passwordChangedReLoginNotice = "마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요."
}
