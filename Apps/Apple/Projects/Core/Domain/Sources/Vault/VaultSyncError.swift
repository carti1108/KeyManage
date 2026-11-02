import Foundation

public enum VaultSyncError: Error, Equatable, Sendable, LocalizedError {
	case payloadTooLarge(Int)
	case revisionRollbackDetected(lastSeen: Int, server: Int)
	case conflictLimitExceeded(attempts: Int)
	case conflict(currentRevision: Int)
	case syncFailed(String)

	public var errorDescription: String? {
		switch self {
		case .payloadTooLarge:
			return VaultSyncStrings.payloadTooLargeMessage
		case .revisionRollbackDetected:
			return VaultSyncStrings.rollbackDetectedMessage
		case .conflictLimitExceeded:
			return VaultSyncStrings.conflictLimitExceededMessage
		case .conflict:
			return "다른 기기에서 볼트가 먼저 변경되었습니다."
		case .syncFailed(let message):
			return message
		}
	}
}
