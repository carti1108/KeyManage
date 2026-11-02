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
		var currentVault = local
		var currentExpectedRevision = lastSeenRevision
		var retries = 0

		guard !currentVault.isReadOnly else {
			throw VaultError.vaultIsReadOnly
		}

		let encoder = JSONEncoder()

		while true {
			let encodedData = try encoder.encode(currentVault)
			try validator.validatePayloadSize(encodedData.count)

			do {
				let newRev = try await saveOperation(currentVault, currentExpectedRevision)
				return (savedVault: currentVault, newRevision: newRev)
			} catch {
				if retries >= Self.maxRetryAttempts {
					throw VaultSyncError.conflictLimitExceeded(attempts: retries)
				}
				guard isConflictError(error) else {
					throw error
				}
				retries += 1
				let (serverVault, serverRevision) = try await fetchOperation()
				try validator.validateRevision(server: serverRevision, lastSeen: currentExpectedRevision)
				currentVault = try merger.merge(local: currentVault, server: serverVault)
				guard !currentVault.isReadOnly else {
					throw VaultError.vaultIsReadOnly
				}
				currentExpectedRevision = serverRevision
			}
		}
	}

	private func isConflictError(_ error: Error) -> Bool {
		if case VaultSyncError.conflict = error {
			return true
		}
		if case VaultSyncError.syncFailed(let msg) = error {
			return msg.contains("409") || msg.lowercased().contains("conflict")
		}
		let errorStr = String(describing: error)
		return errorStr.contains("409") || errorStr.lowercased().contains("conflict")
	}
}
