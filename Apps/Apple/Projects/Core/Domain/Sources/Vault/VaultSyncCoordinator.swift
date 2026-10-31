import Foundation

public struct VaultSyncCoordinator: Sendable {
	public static let maxRetryAttempts: Int = 3

	public let validator: VaultSyncValidator
	public let merger: VaultMerger

	public init(
		validator: VaultSyncValidator = VaultSyncValidator(),
		merger: VaultMerger = VaultMerger()
	) {
		self.validator = validator
		self.merger = merger
	}

	public func syncWithRetry(
		local: VaultData,
		lastSeenRevision: Int,
		saveOperation: @Sendable (VaultData, Int) async throws -> Int,
		fetchOperation: @Sendable () async throws -> (VaultData, Int)
	) async throws -> (savedVault: VaultData, newRevision: Int) {
		throw VaultError.notImplemented
	}
}
