import Foundation

public struct DeviceVaultRecord: Codable, Equatable, Sendable {
	public var email: String
	public var encryptedVault: EncryptedVaultPayload
	public var baseRevision: Int
	public var lastSeenRevision: Int
	public var hasPendingSync: Bool
	public var kdfSalt: Data
	public var kdfIterations: Int

	public init(
		email: String,
		encryptedVault: EncryptedVaultPayload,
		baseRevision: Int,
		lastSeenRevision: Int,
		hasPendingSync: Bool,
		kdfSalt: Data,
		kdfIterations: Int
	) {
		self.email = email
		self.encryptedVault = encryptedVault
		self.baseRevision = baseRevision
		self.lastSeenRevision = lastSeenRevision
		self.hasPendingSync = hasPendingSync
		self.kdfSalt = kdfSalt
		self.kdfIterations = kdfIterations
	}
}

public protocol DeviceVaultStorageProtocol: Sendable {
	func load() async throws -> DeviceVaultRecord?
	func save(_ record: DeviceVaultRecord) async throws
	func delete() async throws
}

public enum DeviceVaultStoreError: Error, Equatable, Sendable {
	case storageSaveFailed(String)
	case storageDeleteFailed(String)
	case recordNotFound
}

public protocol DeviceVaultStoreProtocol: Sendable {
	var hasStorageSaveError: Bool { get }
	var currentRecord: DeviceVaultRecord? { get }
	func loadRecord() async throws -> DeviceVaultRecord?
	func saveRecord(_ record: DeviceVaultRecord) async throws
	func handleAuthenticationSuccess(email: String) async throws
	func handleAuthenticationFailure(email: String) async
	func handleLogout() async throws
	func evaluateKdfChange(newSalt: Data, newIterations: Int) async throws -> Bool
}

public final class DeviceVaultStore: DeviceVaultStoreProtocol, @unchecked Sendable {
	private let lock = NSLock()
	private var _hasStorageSaveError: Bool = false
	private var _currentRecord: DeviceVaultRecord?

	public var hasStorageSaveError: Bool {
		getLockedState().hasError
	}

	public var currentRecord: DeviceVaultRecord? {
		getLockedState().record
	}

	private let storage: DeviceVaultStorageProtocol?

	public init(storage: DeviceVaultStorageProtocol? = nil) {
		self.storage = storage
	}

	private func getLockedState() -> (record: DeviceVaultRecord?, hasError: Bool) {
		lock.lock()
		defer { lock.unlock() }
		return (_currentRecord, _hasStorageSaveError)
	}

	private func updateRecord(_ record: DeviceVaultRecord?, hasError: Bool) {
		lock.lock()
		defer { lock.unlock() }
		_currentRecord = record
		_hasStorageSaveError = hasError
	}

	private func setSaveError(_ hasError: Bool) {
		lock.lock()
		defer { lock.unlock() }
		_hasStorageSaveError = hasError
	}

	public func loadRecord() async throws -> DeviceVaultRecord? {
		if let storage = storage {
			let record = try await storage.load()
			updateRecord(record, hasError: false)
			return record
		} else {
			return currentRecord
		}
	}

	public func saveRecord(_ record: DeviceVaultRecord) async throws {
		do {
			if let storage = storage {
				try await storage.save(record)
			}
			updateRecord(record, hasError: false)
		} catch {
			setSaveError(true)
			throw error
		}
	}

	public func handleAuthenticationSuccess(email: String) async throws {
		var existing = currentRecord

		if existing == nil, let storage = storage {
			existing = try await storage.load()
		}

		if let current = existing, current.email != email {
			do {
				if let storage = storage {
					try await storage.delete()
				}
				updateRecord(nil, hasError: false)
			} catch let err as DeviceVaultStoreError {
				throw err
			} catch {
				throw DeviceVaultStoreError.storageDeleteFailed(error.localizedDescription)
			}
		}
	}

	public func handleAuthenticationFailure(email: String) async {
		// 다른 계정 인증 실패 시에는 이전 기록을 유지
	}

	public func handleLogout() async throws {
		do {
			if let storage = storage {
				try await storage.delete()
			}
			updateRecord(nil, hasError: false)
		} catch let err as DeviceVaultStoreError {
			throw err
		} catch {
			throw DeviceVaultStoreError.storageDeleteFailed(error.localizedDescription)
		}
	}

	public func evaluateKdfChange(newSalt: Data, newIterations: Int) async throws -> Bool {
		var record = currentRecord

		if record == nil, let storage = storage {
			record = try await storage.load()
		}

		guard var rec = record else { return false }
		if rec.kdfSalt != newSalt || rec.kdfIterations != newIterations {
			rec.hasPendingSync = false
			rec.kdfSalt = newSalt
			rec.kdfIterations = newIterations
			if let storage = storage {
				try await storage.save(rec)
			}
			updateRecord(rec, hasError: false)
			return true
		}
		return false
	}
}
