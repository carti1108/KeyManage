import Foundation

public struct SeparatedKeys: Sendable, Equatable {
	public let encryptionKey: Data
	public let authHash: Data

	public init(encryptionKey: Data, authHash: Data) {
		self.encryptionKey = encryptionKey
		self.authHash = authHash
	}
}

public struct HKDFKeySeparator: Sendable {
	public static let encryptionInfo = "keymanage-enc"
	public static let authInfo = "keymanage-auth"
	public static let keyLength: Int = 32

	public static func separate(masterKey: Data) throws -> SeparatedKeys {
		throw KeyDerivationError.derivationFailed(-1)
	}

	public static func deriveEncryptionKey(masterKey: Data) throws -> Data {
		throw KeyDerivationError.derivationFailed(-1)
	}

	public static func deriveAuthHash(masterKey: Data) throws -> Data {
		throw KeyDerivationError.derivationFailed(-1)
	}
}
