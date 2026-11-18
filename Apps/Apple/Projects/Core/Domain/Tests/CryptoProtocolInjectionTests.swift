import Foundation
import Testing
@testable import KeyManageDomain

// MARK: - Test Doubles

private final class SpyMasterKeyDeriver: MasterKeyDeriverProtocol, @unchecked Sendable {
	private let lock = NSLock()
	private var _callCount = 0
	var callCount: Int { lock.withLock { _callCount } }

	func deriveMasterKey(password: Data, salt: Data, iterations: Int) throws -> Data {
		lock.withLock { _callCount += 1 }
		return Data(repeating: 0x01, count: 32)
	}
}

private struct StubKeySeparator: KeySeparatorProtocol {
	func separate(masterKey: Data) throws -> SeparatedKeys {
		SeparatedKeys(encryptionKey: Data(repeating: 0x02, count: 32), authHash: Data(repeating: 0x03, count: 32))
	}

	func deriveEncryptionKey(masterKey: Data) throws -> Data {
		Data(repeating: 0x02, count: 32)
	}
}

private struct StubDecryptFailure: Error {}

private final class SpyVaultCipher: VaultCipherProtocol, @unchecked Sendable {
	private let lock = NSLock()
	private var _decryptCallCount = 0
	var decryptCallCount: Int { lock.withLock { _decryptCallCount } }

	func encrypt(plaintext: Data, key: Data) throws -> EncryptedVaultPayload {
		try VaultCipher.encrypt(plaintext: plaintext, key: key)
	}

	func decrypt(payload: EncryptedVaultPayload, key: Data) throws -> Data {
		lock.withLock { _decryptCallCount += 1 }
		throw StubDecryptFailure()
	}
}

// MARK: - CryptoProtocolInjectionTests Suite

@Suite("CryptoProtocolInjectionTests - 암호 프로토콜 기본 구현과 주입 검증")
struct CryptoProtocolInjectionTests {

	@Test("기본 구현의 인스턴스 메서드는 정적 메서드와 같은 결과를 낸다")
	func defaultConformers_matchStaticImplementations() throws {
		// Given
		let password = Data("correct horse battery staple".utf8)
		let salt = Data(repeating: 0xAB, count: PBKDF2KeyDeriver.expectedSaltLength)
		let iterations = PBKDF2KeyDeriver.defaultIterations
		let deriver: any MasterKeyDeriverProtocol = PBKDF2KeyDeriver()
		let separator: any KeySeparatorProtocol = HKDFKeySeparator()
		let cipher: any VaultCipherProtocol = VaultCipher()

		// When
		let masterKey = try deriver.deriveMasterKey(password: password, salt: salt, iterations: iterations)
		let keys = try separator.separate(masterKey: masterKey)
		let payload = try cipher.encrypt(plaintext: Data("vault".utf8), key: keys.encryptionKey)
		let plaintext = try cipher.decrypt(payload: payload, key: keys.encryptionKey)

		// Then
		#expect(masterKey == (try PBKDF2KeyDeriver.deriveMasterKey(password: password, salt: salt, iterations: iterations)))
		#expect(keys == (try HKDFKeySeparator.separate(masterKey: masterKey)))
		#expect(try separator.deriveEncryptionKey(masterKey: masterKey) == keys.encryptionKey)
		#expect(plaintext == Data("vault".utf8))
		#expect(try VaultCipher.decrypt(payload: payload, key: keys.encryptionKey) == plaintext)
	}

	@Test("VaultLockManager는 잠금 해제 시 주입된 키 유도기와 암호기를 사용한다")
	func vaultLockManager_unlock_usesInjectedCrypto() throws {
		// Given
		let deriver = SpyMasterKeyDeriver()
		let cipher = SpyVaultCipher()
		let manager = VaultLockManager(keyDeriver: deriver, keySeparator: StubKeySeparator(), vaultCipher: cipher)
		let dummyPayload = EncryptedVaultPayload(
			version: 1,
			cipher: VaultCipher.supportedCipher,
			iv: Data(count: VaultCipher.expectedIVLength).base64EncodedString(),
			ciphertext: Data("x".utf8).base64EncodedString(),
			tag: Data(count: VaultCipher.expectedTagLength).base64EncodedString()
		)
		manager.setupSession(
			email: "spy@example.com",
			sessionToken: "token",
			salt: Data(repeating: 0xCD, count: PBKDF2KeyDeriver.expectedSaltLength),
			iterations: PBKDF2KeyDeriver.defaultIterations,
			encryptedVault: dummyPayload,
			isBiometricsEnabled: false,
			biometryStateHash: nil,
			revision: 1
		)
		// setupSession은 잠금 해제 상태로 시작하므로, 잠긴 볼트에서 해제를 시도하는 상황을 만든다
		manager.lock()

		// When
		#expect(throws: (any Error).self) {
			try manager.unlockWithMasterPassword(password: "any-master-password", now: Date())
		}

		// Then
		#expect(deriver.callCount == 1)
		#expect(cipher.decryptCallCount == 1)
		#expect(manager.isLocked == true)
	}
}
