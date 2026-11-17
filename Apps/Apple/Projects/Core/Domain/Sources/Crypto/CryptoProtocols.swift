import Foundation

/// 마스터 비밀번호에서 마스터 키를 유도한다. 기본 구현은 `PBKDF2KeyDeriver`.
public protocol MasterKeyDeriverProtocol: Sendable {
	func deriveMasterKey(password: Data, salt: Data, iterations: Int) throws -> Data
}

/// 마스터 키를 암호화 키와 인증 해시로 분리한다. 기본 구현은 `HKDFKeySeparator`.
public protocol KeySeparatorProtocol: Sendable {
	func separate(masterKey: Data) throws -> SeparatedKeys
	func deriveEncryptionKey(masterKey: Data) throws -> Data
}

/// 볼트 평문을 암·복호화한다. 기본 구현은 `VaultCipher`.
public protocol VaultCipherProtocol: Sendable {
	func encrypt(plaintext: Data, key: Data) throws -> EncryptedVaultPayload
	func decrypt(payload: EncryptedVaultPayload, key: Data) throws -> Data
}

extension PBKDF2KeyDeriver: MasterKeyDeriverProtocol {
	public func deriveMasterKey(password: Data, salt: Data, iterations: Int) throws -> Data {
		try Self.deriveMasterKey(password: password, salt: salt, iterations: iterations)
	}
}

extension HKDFKeySeparator: KeySeparatorProtocol {
	public func separate(masterKey: Data) throws -> SeparatedKeys {
		try Self.separate(masterKey: masterKey)
	}

	public func deriveEncryptionKey(masterKey: Data) throws -> Data {
		try Self.deriveEncryptionKey(masterKey: masterKey)
	}
}

extension VaultCipher: VaultCipherProtocol {
	public func encrypt(plaintext: Data, key: Data) throws -> EncryptedVaultPayload {
		try Self.encrypt(plaintext: plaintext, key: key)
	}

	public func decrypt(payload: EncryptedVaultPayload, key: Data) throws -> Data {
		try Self.decrypt(payload: payload, key: key)
	}
}
