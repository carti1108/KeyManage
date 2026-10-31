import Foundation

public struct VaultSyncValidator: Sendable {
	public static let maxPayloadSizeBytes: Int = 5_242_880

	public init() {}

	public func validatePayloadSize(_ sizeInBytes: Int) throws {
		if sizeInBytes > Self.maxPayloadSizeBytes {
			throw VaultSyncError.payloadTooLarge(sizeInBytes)
		}
	}

	public func validateRevision(server: Int, lastSeen: Int) throws {
		if server < lastSeen {
			throw VaultSyncError.revisionRollbackDetected(lastSeen: lastSeen, server: server)
		}
	}
}
