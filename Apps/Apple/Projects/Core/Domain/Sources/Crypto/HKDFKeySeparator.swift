import Foundation
import CryptoKit

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
		let encryptionKey = try deriveEncryptionKey(masterKey: masterKey)
		let authHash = try deriveAuthHash(masterKey: masterKey)
		return SeparatedKeys(encryptionKey: encryptionKey, authHash: authHash)
	}

	public static func deriveEncryptionKey(masterKey: Data) throws -> Data {
		try deriveSubkey(masterKey: masterKey, info: encryptionInfo)
	}

	public static func deriveAuthHash(masterKey: Data) throws -> Data {
		try deriveSubkey(masterKey: masterKey, info: authInfo)
	}

	private static func deriveSubkey(masterKey: Data, info: String) throws -> Data {
		let inputKey = SymmetricKey(data: masterKey)
		guard let infoData = info.data(using: .utf8) else {
			throw KeyDerivationError.derivationFailed(-1)
		}

		let derived = HKDF<SHA256>.deriveKey(
			inputKeyMaterial: inputKey,
			salt: Data(),
			info: infoData,
			outputByteCount: keyLength
		)

		return derived.withUnsafeBytes { Data($0) }
	}
}
