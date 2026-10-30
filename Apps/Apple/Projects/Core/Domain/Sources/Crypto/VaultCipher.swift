import Foundation

public enum VaultCipherError: Error, Equatable, Sendable {
	case invalidFormat(String)
	case unsupportedCipher(String)
	case invalidVersion(Int)
	case authenticationFailed
	case encryptionFailed(String)
}

public struct VaultCipher: Sendable {
	public static let supportedCipher = "AES-256-GCM"
	public static let expectedIVLength = 12
	public static let expectedTagLength = 16

	public static func makeAAD(version: Int, cipher: String) -> Data {
		Data("keymanage-vault|\(version)|\(cipher)".utf8)
	}

	public static func encrypt(
		plaintext: Data,
		key: Data,
		version: Int = 1,
		cipher: String = supportedCipher,
		iv: Data? = nil
	) throws -> EncryptedVaultPayload {
		throw VaultCipherError.encryptionFailed("Not implemented")
	}

	public static func decrypt(
		payload: EncryptedVaultPayload,
		key: Data
	) throws -> Data {
		throw VaultCipherError.authenticationFailed
	}
}
