import Foundation

public enum VaultItemError: LocalizedError, Sendable, Equatable {
	case emptyServiceName
	case vaultIsReadOnly
	case vaultTooLarge
	case itemNotFound
	case locked

	public var errorDescription: String? {
		switch self {
		case .emptyServiceName:
			return "서비스 이름을 입력해 주세요."
		case .vaultIsReadOnly:
			return "새 버전 앱에서 저장한 볼트입니다. 앱을 업데이트하기 전까지는 읽기만 할 수 있습니다."
		case .vaultTooLarge:
			return "볼트 허용 용량(5MB)을 초과했거나 데이터 규격이 올바르지 않습니다."
		case .itemNotFound:
			return "항목을 찾을 수 없습니다."
		case .locked:
			return "볼트가 잠겨 있습니다."
		}
	}
}
