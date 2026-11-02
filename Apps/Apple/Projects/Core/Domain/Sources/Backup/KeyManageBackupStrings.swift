import Foundation

public enum KeyManageBackupStrings: Sendable {
	public static let exportTitle = "백업 내보내기"
	public static let exportDescription = "이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다."
	public static let exportButton = "내보내기"

	public static let decryptFailedMessage = "백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요."
	public static let notABackupFileMessage = "KeyManage 백업 파일이 아닙니다."
	public static let newerVersionMessage = "더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요."

	public static let restoreTitle = "백업에서 복원"
	public static let restoreDescription = "백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다."
	public static let restoreButton = "복원"

	public static func restoreCompletedMessage(count: Int) -> String {
		"백업에서 항목 \(count)개를 복원했습니다."
	}
}
