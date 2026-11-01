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
	public private(set) var hasStorageSaveError: Bool = false
	public private(set) var currentRecord: DeviceVaultRecord? = nil

	private let storage: DeviceVaultStorageProtocol?

	public init(storage: DeviceVaultStorageProtocol? = nil) {
		self.storage = storage
	}

	public func loadRecord() async throws -> DeviceVaultRecord? {
		return nil
	}

	public func saveRecord(_ record: DeviceVaultRecord) async throws {
		// RED 스텁: 저장 로직 미구현으로 테스트 실패
	}

	public func handleAuthenticationSuccess(email: String) async throws {
		// RED 스텁: 계정 변경 삭제 로직 미구현
	}

	public func handleAuthenticationFailure(email: String) async {
		// RED 스텁
	}

	public func handleLogout() async throws {
		// RED 스텁: 로그아웃 삭제 로직 미구현
	}

	public func evaluateKdfChange(newSalt: Data, newIterations: Int) async throws -> Bool {
		return false
	}
}
