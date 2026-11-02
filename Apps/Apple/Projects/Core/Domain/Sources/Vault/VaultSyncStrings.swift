import Foundation

public enum VaultSyncStrings {
	// MARK: - VLT-001 사용자 문구
	public static let emptyServiceNameMessage = "서비스 이름을 입력해 주세요."
	public static let deleteItemConfirmTitle = "항목 삭제"
	public static let deleteItemConfirmMessage = "이 항목을 삭제할까요? 다른 기기에서도 삭제됩니다."
	public static let deleteButton = "삭제"
	public static let cancelButton = "취소"

	public static let payloadTooLargeTitle = "저장 실패"
	public static let payloadTooLargeMessage = "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다."

	public static let conflictLimitExceededTitle = "동기화 충돌"
	public static let conflictLimitExceededMessage = "다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요."

	public static let rollbackDetectedTitle = "서버 볼트가 이전 상태로 돌아감"
	public static let rollbackDetectedMessage = "서버의 볼트가 이 기기에서 마지막으로 본 상태보다 이전으로 돌아갔습니다. 서버를 복구한 경우가 아니라면 받아들이지 마세요."
	public static let rollbackAcceptButton = "서버 볼트 받아들이기"
	public static let rollbackCancelButton = "취소"

	public static let readOnlyNotice = "새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다."

	public static let invalidFormatTitle = "볼트를 열 수 없음"
	public static let invalidFormatMessage = "볼트 데이터 형식이 올바르지 않습니다."

	// MARK: - VLT-002 사용자 문구
	public static let offlineWarningMessage = "오프라인 상태입니다. 변경 사항이 서버에 동기화되지 않습니다."
	public static let pendingLogoutConfirmTitle = "동기화되지 않은 변경 사항"
	public static let pendingLogoutConfirmMessage = "로그아웃하면 서버에 올리지 못한 변경 사항이 사라집니다."
	public static let logoutButton = "로그아웃"
}
