import Foundation

public struct PreloginResponse: Codable, Sendable, Equatable {
	public let kdf: String
	public let salt: String
	public let iterations: Int

	public init(kdf: String, salt: String, iterations: Int) {
		self.kdf = kdf
		self.salt = salt
		self.iterations = iterations
	}
}

public struct ChangePasswordRequestPayload: Codable, Sendable, Equatable {
	public struct KdfInfo: Codable, Sendable, Equatable {
		public let iterations: Int
		public let salt: String

		public init(iterations: Int, salt: String) {
			self.iterations = iterations
			self.salt = salt
		}
	}

	public struct VaultInfo: Codable, Sendable, Equatable {
		public let expectedRevision: Int
		public let payload: EncryptedVaultPayload

		public init(expectedRevision: Int, payload: EncryptedVaultPayload) {
			self.expectedRevision = expectedRevision
			self.payload = payload
		}
	}

	public let currentAuthHash: String
	public let newAuthHash: String
	public let kdf: KdfInfo
	public let vault: VaultInfo

	public init(
		currentAuthHash: String,
		newAuthHash: String,
		kdf: KdfInfo,
		vault: VaultInfo
	) {
		self.currentAuthHash = currentAuthHash
		self.newAuthHash = newAuthHash
		self.kdf = kdf
		self.vault = vault
	}
}

public struct ChangePasswordResponsePayload: Codable, Sendable, Equatable {
	public let accessToken: String
	public let expiresAt: String
	public let revision: Int

	public init(accessToken: String, expiresAt: String, revision: Int) {
		self.accessToken = accessToken
		self.expiresAt = expiresAt
		self.revision = revision
	}
}

public struct DeleteAccountRequestPayload: Codable, Sendable, Equatable {
	public let authHash: String

	public init(authHash: String) {
		self.authHash = authHash
	}
}

public protocol AuthNetworkClientProtocol: Sendable {
	func prelogin(email: String) async throws -> PreloginResponse
	func changePassword(
		accessToken: String,
		request: ChangePasswordRequestPayload
	) async throws -> ChangePasswordResponsePayload
	func deleteAccount(
		accessToken: String,
		authHash: String
	) async throws
}
