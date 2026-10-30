import Foundation

public struct EncryptedVaultPayload: Codable, Equatable, Sendable {
	public let version: Int
	public let cipher: String
	public let iv: String
	public let ciphertext: String
	public let tag: String

	public init(
		version: Int = 1,
		cipher: String = "AES-256-GCM",
		iv: String,
		ciphertext: String,
		tag: String
	) {
		self.version = version
		self.cipher = cipher
		self.iv = iv
		self.ciphertext = ciphertext
		self.tag = tag
	}
}
