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

public struct RegisterRequestPayload: Codable, Sendable, Equatable {
	public let email: String
	public let authHash: String
	public let kdfSalt: String
	public let kdfIterations: Int

	public init(email: String, authHash: String, kdfSalt: String, kdfIterations: Int) {
		self.email = email
		self.authHash = authHash
		self.kdfSalt = kdfSalt
		self.kdfIterations = kdfIterations
	}
}

public struct RegisterResponsePayload: Codable, Sendable, Equatable {
	public let accessToken: String
	public let expiresAt: String

	public init(accessToken: String, expiresAt: String) {
		self.accessToken = accessToken
		self.expiresAt = expiresAt
	}
}

public struct LoginRequestPayload: Codable, Sendable, Equatable {
	public let email: String
	public let authHash: String

	public init(email: String, authHash: String) {
		self.email = email
		self.authHash = authHash
	}
}

public struct LoginResponsePayload: Codable, Sendable, Equatable {
	public let accessToken: String
	public let expiresAt: String

	public init(accessToken: String, expiresAt: String) {
		self.accessToken = accessToken
		self.expiresAt = expiresAt
	}
}

public struct GetVaultResponsePayload: Codable, Sendable, Equatable {
	public let revision: Int
	public let payload: EncryptedVaultPayload

	public init(revision: Int, payload: EncryptedVaultPayload) {
		self.revision = revision
		self.payload = payload
	}
}

public protocol AuthNetworkClientProtocol: Sendable {
	func prelogin(email: String) async throws -> PreloginResponse
	func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload
	func login(request: LoginRequestPayload) async throws -> LoginResponsePayload
	func getVault(accessToken: String) async throws -> GetVaultResponsePayload?
	func changePassword(
		accessToken: String,
		request: ChangePasswordRequestPayload
	) async throws -> ChangePasswordResponsePayload
	func deleteAccount(
		accessToken: String,
		authHash: String
	) async throws
}
