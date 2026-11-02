import Foundation

/// SEC-005 계정 삭제 표준 사용자 문구 (SSOT: docs/policies/SEC/SEC-005-account-deletion.md)
public enum AccountDeletionStrings {
	public static let preNoticeTitle = "계정 삭제"
	public static let preNoticeBody = "계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요."
	public static let confirmationPrompt = "확인을 위해 '계정 삭제'를 입력해 주세요."
	public static let confirmationPhrase = "계정 삭제"
	public static let invalidPasswordMessage = "마스터 비밀번호가 올바르지 않습니다."
	public static let offlineMessage = "인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다."
	public static let successMessage = "계정을 삭제했습니다."
	public static let tooManyAttemptsMessage = "시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."
	public static let sessionExpiredMessage = "세션이 만료되었습니다. 다시 로그인해 주세요."
	public static let deviceClearFailedMessage = "계정은 삭제했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다."
	public static let deleteButtonTitle = "계정 삭제"
	public static let cancelButtonTitle = "취소"
}
