import Foundation
import CryptoKit

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
	public static let expectedKeyLength = 32

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
		guard version >= 1 else {
			throw VaultCipherError.invalidVersion(version)
		}
		guard cipher == supportedCipher else {
			throw VaultCipherError.unsupportedCipher(cipher)
		}
		guard key.count == expectedKeyLength else {
			throw VaultCipherError.invalidFormat("Key must be \(expectedKeyLength) bytes")
		}

		let nonce: AES.GCM.Nonce
		if let customIV = iv {
			guard customIV.count == expectedIVLength else {
				throw VaultCipherError.invalidFormat("IV must be \(expectedIVLength) bytes")
			}
			guard let customNonce = try? AES.GCM.Nonce(data: customIV) else {
				throw VaultCipherError.invalidFormat("Invalid nonce data")
			}
			nonce = customNonce
		} else {
			nonce = AES.GCM.Nonce()
		}

		let aad = makeAAD(version: version, cipher: cipher)
		let symmetricKey = SymmetricKey(data: key)

		do {
			let sealed = try AES.GCM.seal(plaintext, using: symmetricKey, nonce: nonce, authenticating: aad)
			let ivData = Data(nonce)
			let ciphertextData = sealed.ciphertext
			let tagData = sealed.tag

			return EncryptedVaultPayload(
				version: version,
				cipher: cipher,
				iv: ivData.base64EncodedString(),
				ciphertext: ciphertextData.base64EncodedString(),
				tag: tagData.base64EncodedString()
			)
		} catch {
			throw VaultCipherError.encryptionFailed(error.localizedDescription)
		}
	}

	public static func decrypt(
		payload: EncryptedVaultPayload,
		key: Data
	) throws -> Data {
		guard payload.version >= 1 else {
			throw VaultCipherError.invalidVersion(payload.version)
		}
		guard payload.cipher == supportedCipher else {
			throw VaultCipherError.unsupportedCipher(payload.cipher)
		}
		guard key.count == expectedKeyLength else {
			throw VaultCipherError.invalidFormat("Key must be \(expectedKeyLength) bytes")
		}

		guard let ivData = Data(base64Encoded: payload.iv), ivData.count == expectedIVLength else {
			throw VaultCipherError.invalidFormat("IV must be valid Base64 and \(expectedIVLength) bytes")
		}
		guard let tagData = Data(base64Encoded: payload.tag), tagData.count == expectedTagLength else {
			throw VaultCipherError.invalidFormat("Tag must be valid Base64 and \(expectedTagLength) bytes")
		}
		guard let ciphertextData = Data(base64Encoded: payload.ciphertext) else {
			throw VaultCipherError.invalidFormat("Ciphertext must be valid Base64")
		}

		guard let nonce = try? AES.GCM.Nonce(data: ivData) else {
			throw VaultCipherError.invalidFormat("Invalid nonce format")
		}

		let aad = makeAAD(version: payload.version, cipher: payload.cipher)
		let symmetricKey = SymmetricKey(data: key)

		do {
			let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertextData, tag: tagData)
			return try AES.GCM.open(sealedBox, using: symmetricKey, authenticating: aad)
		} catch {
			throw VaultCipherError.authenticationFailed
		}
	}
}
