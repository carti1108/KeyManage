import Foundation

public enum VaultSyncError: Error, Equatable, Sendable {
	case payloadTooLarge(Int)
	case revisionRollbackDetected(lastSeen: Int, server: Int)
	case conflictLimitExceeded(attempts: Int)
	case syncFailed(String)
}
