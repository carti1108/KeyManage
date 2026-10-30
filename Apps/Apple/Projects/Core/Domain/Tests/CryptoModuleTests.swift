import Foundation
import XCTest
@testable import KeyManageDomain

final class CryptoModuleTests: XCTestCase {

	// MARK: - 1. PasswordPreprocessor Tests

	func testPasswordPreprocessorVectors() throws {
		let testCases: [(password: String, expectedB64: String)] = [
			("correct horse battery staple", "Y29ycmVjdCBob3JzZSBiYXR0ZXJ5IHN0YXBsZQ=="),
			("한글 비밀번호 🔑 2026", "7ZWc6riAIOu5hOuwgOuyiO2YuCDwn5SRIDIwMjY="),
			("Café au lait", "Q2Fmw6kgYXUgbGFpdA=="),
			("Cafe\u{0301} au lait", "Q2Fmw6kgYXUgbGFpdA=="),
			(" \t correct horse battery staple　\n", "Y29ycmVjdCBob3JzZSBiYXR0ZXJ5IHN0YXBsZQ==")
		]

		for item in testCases {
			let result = try PasswordPreprocessor.preprocess(item.password)
			XCTAssertEqual(result.base64EncodedString(), item.expectedB64)
		}
	}

	func testPasswordPreprocessorWhitespaceTrimming() throws {
		let input = "\u{0020}\u{0009}\u{000A}\u{000D}\u{00A0}\u{3000}inner space kept\u{3000}\u{00A0}\u{000D}\u{000A}\u{0009}\u{0020}"
		let result = try PasswordPreprocessor.preprocess(input)
		let str = String(data: result, encoding: .utf8)
		XCTAssertEqual(str, "inner space kept")
	}

	func testPasswordPreprocessorRejectsUnpairedSurrogates() {
		// Lone high surrogate
		let loneHigh: [UInt16] = [0x0041, 0xD800, 0x0042]
		XCTAssertThrowsError(try PasswordPreprocessor.preprocess(utf16CodeUnits: loneHigh)) { error in
			XCTAssertEqual(error as? PasswordPreprocessingError, .unpairedSurrogate)
		}

		// Lone low surrogate
		let loneLow: [UInt16] = [0x0041, 0xDC00, 0x0042]
		XCTAssertThrowsError(try PasswordPreprocessor.preprocess(utf16CodeUnits: loneLow)) { error in
			XCTAssertEqual(error as? PasswordPreprocessingError, .unpairedSurrogate)
		}

		// Valid surrogate pair succeeds
		let validPair: [UInt16] = [0x0041, 0xD83D, 0xDD11, 0x0042] // A, 🔑, B
		XCTAssertNoThrow(try PasswordPreprocessor.preprocess(utf16CodeUnits: validPair))
	}

	// MARK: - 2. PBKDF2KeyDeriver Tests

	func testPBKDF2KeyDerivationVectors() throws {
		struct Vector {
			let password: Data
			let salt: Data
			let expectedMasterKeyB64: String
		}

		let vectors: [Vector] = [
			// ascii-passphrase
			Vector(
				password: Data(base64Encoded: "Y29ycmVjdCBob3JzZSBiYXR0ZXJ5IHN0YXBsZQ==")!,
				salt: Data(base64Encoded: "AAECAwQFBgcICQoLDA0ODw==")!,
				expectedMasterKeyB64: "7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY="
			),
			// korean-emoji
			Vector(
				password: Data(base64Encoded: "7ZWc6riAIOu5hOuwgOuyiO2YuCDwn5SRIDIwMjY=")!,
				salt: Data(base64Encoded: "EBESExQVFhcYGRobHB0eHw==")!,
				expectedMasterKeyB64: "dZ/lajwXROENmpBKiQcY8UXnXdzb1DBoPILJdeFywko="
			),
			// nfc-composed
			Vector(
				password: Data(base64Encoded: "Q2Fmw6kgYXUgbGFpdA==")!,
				salt: Data(base64Encoded: "ICEiIyQlJicoKSorLC0uLw==")!,
				expectedMasterKeyB64: "f0Byn9WCX4A/tL/CgoCIGrCBXqpzNklvQlZW5p2NPTY="
			)
		]

		for vec in vectors {
			let derived = try PBKDF2KeyDeriver.deriveMasterKey(password: vec.password, salt: vec.salt, iterations: 600_000)
			XCTAssertEqual(derived.base64EncodedString(), vec.expectedMasterKeyB64)
		}
	}

	func testPBKDF2InvalidSaltLength() {
		let password = Data("password".utf8)
		let invalidSalt15 = Data(count: 15)
		let invalidSalt17 = Data(count: 17)

		XCTAssertThrowsError(try PBKDF2KeyDeriver.deriveMasterKey(password: password, salt: invalidSalt15)) { error in
			XCTAssertEqual(error as? KeyDerivationError, .invalidSaltLength(15))
		}
		XCTAssertThrowsError(try PBKDF2KeyDeriver.deriveMasterKey(password: password, salt: invalidSalt17)) { error in
			XCTAssertEqual(error as? KeyDerivationError, .invalidSaltLength(17))
		}
	}

	func testPBKDF2IterationBounds() {
		let password = Data("password".utf8)
		let salt = Data(count: 16)

		// Below 600,000
		XCTAssertThrowsError(try PBKDF2KeyDeriver.deriveMasterKey(password: password, salt: salt, iterations: 599_999)) { error in
			XCTAssertEqual(error as? KeyDerivationError, .invalidIterationCount(599_999))
		}

		// Above 10,000,000
		XCTAssertThrowsError(try PBKDF2KeyDeriver.deriveMasterKey(password: password, salt: salt, iterations: 10_000_001)) { error in
			XCTAssertEqual(error as? KeyDerivationError, .invalidIterationCount(10_000_001))
		}
	}

	// MARK: - 3. HKDFKeySeparator Tests

	func testHKDFKeySeparatorVectors() throws {
		struct HKDFVector {
			let masterKeyB64: String
			let expectedEncKeyB64: String
			let expectedAuthHashB64: String
		}

		let vectors: [HKDFVector] = [
			// ascii-passphrase
			HKDFVector(
				masterKeyB64: "7xdxRO7JQgy8EJPSqLNEqSvFBtDU7JwCjdGfgyTYweY=",
				expectedEncKeyB64: "fMgSdigbsNMdevy2GjTgQTVdaKTKwzfbVXVgmg3ouF0=",
				expectedAuthHashB64: "cp+w6ZfEyRIoVXtIAS8F9OVsq1zq0sxWMFcD02t0bgA="
			),
			// korean-emoji
			HKDFVector(
				masterKeyB64: "dZ/lajwXROENmpBKiQcY8UXnXdzb1DBoPILJdeFywko=",
				expectedEncKeyB64: "FhWJTMF6BTQGljYtmyTZIK7aEn5AAAiwDCdJGwp5RzI=",
				expectedAuthHashB64: "QlniXMhrwerwT1oIwVv2CTL3Nlk9NNHNtE4hQsiTMFE="
			),
			// nfc-composed
			HKDFVector(
				masterKeyB64: "f0Byn9WCX4A/tL/CgoCIGrCBXqpzNklvQlZW5p2NPTY=",
				expectedEncKeyB64: "yxK4B1Fw+Cfv6UAx5I/9CcBT5Rc5KQG3hd6lQ41J3eU=",
				expectedAuthHashB64: "lBnwg63aiCTtrlC8OYr0C4vykJ0JXJE8JKO5EDjImlE="
			)
		]

		for vec in vectors {
			let masterKey = Data(base64Encoded: vec.masterKeyB64)!
			let separated = try HKDFKeySeparator.separate(masterKey: masterKey)
			XCTAssertEqual(separated.encryptionKey.base64EncodedString(), vec.expectedEncKeyB64)
			XCTAssertEqual(separated.authHash.base64EncodedString(), vec.expectedAuthHashB64)
			XCTAssertNotEqual(separated.encryptionKey, separated.authHash)
		}
	}

	// MARK: - 4. VaultCipher Tests

	func testVaultEncryptionAndDecryptionVector() throws {
		let keyData = Data(base64Encoded: "fMgSdigbsNMdevy2GjTgQTVdaKTKwzfbVXVgmg3ouF0=")!
		let ivData = Data(base64Encoded: "AAECAwQFBgcICQoL")!
		let plaintext = "{\"version\":1,\"updatedAt\":\"2026-10-27T11:20:00Z\",\"items\":[{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"serviceName\":\"GitHub\",\"username\":\"octocat@github.com\",\"password\":\"SuperSecurePassword123!\",\"url\":\"https://github.com/login\",\"notes\":\"개인 개발용 서브 계정\",\"createdAt\":\"2026-10-27T10:00:00Z\",\"updatedAt\":\"2026-10-27T11:20:00Z\"}]}"
		let expectedCiphertextB64 = "/cHDi35Q88F8cMoQE46t7S9mMLsgOB3avqLDYMKZvDgWGAJa6g+TR4TpsMHHKoDmQl++IBrrIXer/CmwrVRfC5ZzzeV63nD6AzacBUM5hL9ARTMCNiixw9H21EtxABVirdXlnlzUCtJfldn2BKFBhiIRgkO/x+L96VZxkZk5hslRUs1f4eD0T35YUnEk4tAldgzGSbHwRAQmtAj5MGizxXc7k4+OmdDYWLZlR9BGj6licdpuIqTl4SUZM2iLLEZlo9YXErfuwvVA46M9yLCU7+Xt5MtYvB3NT9S6jD6WFpPOiNfdpdqtIAtMfVf4XFl22HBYsyB0EfqJ1Q1ZMmj1rZsvkoyX7LN+9jwKw9cNYK66UjS69Xq3RShDVyXdRfOLWb66QTeZAacRfv95w7UMnIu467OWZjJ0bCRN0N2m6gJ4bfxeW+frYBN9qKsem4QmbnHe6w=="
		let expectedTagB64 = "m/pAKudvkC23PuTKF+nTcw=="

		let payload = try VaultCipher.encrypt(
			plaintext: Data(plaintext.utf8),
			key: keyData,
			version: 1,
			cipher: "AES-256-GCM",
			iv: ivData
		)

		XCTAssertEqual(payload.version, 1)
		XCTAssertEqual(payload.cipher, "AES-256-GCM")
		XCTAssertEqual(payload.iv, "AAECAwQFBgcICQoL")
		XCTAssertEqual(payload.ciphertext, expectedCiphertextB64)
		XCTAssertEqual(payload.tag, expectedTagB64)

		// Decrypt
		let decrypted = try VaultCipher.decrypt(payload: payload, key: keyData)
		XCTAssertEqual(String(data: decrypted, encoding: .utf8), plaintext)
	}

	func testVaultRandomIVGeneration() throws {
		let keyData = Data(count: 32)
		let plaintext = Data("hello world".utf8)

		let payload1 = try VaultCipher.encrypt(plaintext: plaintext, key: keyData)
		let payload2 = try VaultCipher.encrypt(plaintext: plaintext, key: keyData)

		XCTAssertNotEqual(payload1.iv, payload2.iv)
		XCTAssertNotEqual(payload1.ciphertext, payload2.ciphertext)

		let dec1 = try VaultCipher.decrypt(payload: payload1, key: keyData)
		let dec2 = try VaultCipher.decrypt(payload: payload2, key: keyData)
		XCTAssertEqual(dec1, plaintext)
		XCTAssertEqual(dec2, plaintext)
	}

	func testVaultDecryptionWrongKeyRejection() throws {
		let keyData = Data(count: 32)
		var wrongKey = keyData
		wrongKey[0] ^= 1

		let payload = try VaultCipher.encrypt(plaintext: Data("secret".utf8), key: keyData)

		XCTAssertThrowsError(try VaultCipher.decrypt(payload: payload, key: wrongKey)) { error in
			XCTAssertEqual(error as? VaultCipherError, .authenticationFailed)
		}
	}

	func testVaultDecryptionTamperedCiphertextRejection() throws {
		let keyData = Data(count: 32)
		let payload = try VaultCipher.encrypt(plaintext: Data("secret".utf8), key: keyData)

		var cipherBytes = Data(base64Encoded: payload.ciphertext)!
		cipherBytes[0] ^= 1
		let tamperedPayload = EncryptedVaultPayload(
			version: payload.version,
			cipher: payload.cipher,
			iv: payload.iv,
			ciphertext: cipherBytes.base64EncodedString(),
			tag: payload.tag
		)

		XCTAssertThrowsError(try VaultCipher.decrypt(payload: tamperedPayload, key: keyData)) { error in
			XCTAssertEqual(error as? VaultCipherError, .authenticationFailed)
		}
	}

	func testVaultDecryptionTamperedTagRejection() throws {
		let keyData = Data(count: 32)
		let payload = try VaultCipher.encrypt(plaintext: Data("secret".utf8), key: keyData)

		var tagBytes = Data(base64Encoded: payload.tag)!
		tagBytes[0] ^= 1
		let tamperedPayload = EncryptedVaultPayload(
			version: payload.version,
			cipher: payload.cipher,
			iv: payload.iv,
			ciphertext: payload.ciphertext,
			tag: tagBytes.base64EncodedString()
		)

		XCTAssertThrowsError(try VaultCipher.decrypt(payload: tamperedPayload, key: keyData)) { error in
			XCTAssertEqual(error as? VaultCipherError, .authenticationFailed)
		}
	}

	func testVaultDecryptionTamperedHeaderAADRejection() throws {
		let keyData = Data(count: 32)
		let payload = try VaultCipher.encrypt(plaintext: Data("secret".utf8), key: keyData)

		// Tamper version in header (AAD mismatch)
		let tamperedVersionPayload = EncryptedVaultPayload(
			version: 2,
			cipher: payload.cipher,
			iv: payload.iv,
			ciphertext: payload.ciphertext,
			tag: payload.tag
		)

		XCTAssertThrowsError(try VaultCipher.decrypt(payload: tamperedVersionPayload, key: keyData)) { error in
			XCTAssertEqual(error as? VaultCipherError, .authenticationFailed)
		}
	}

	func testVaultFormatErrors() {
		let keyData = Data(count: 32)

		// Invalid IV length (!= 12 bytes)
		let invalidIVPayload = EncryptedVaultPayload(
			version: 1,
			cipher: "AES-256-GCM",
			iv: Data(count: 16).base64EncodedString(),
			ciphertext: "AA==",
			tag: Data(count: 16).base64EncodedString()
		)
		XCTAssertThrowsError(try VaultCipher.decrypt(payload: invalidIVPayload, key: keyData)) { error in
			if case .invalidFormat = (error as? VaultCipherError) {
				// expected
			} else {
				XCTFail("Expected invalidFormat, got \(error)")
			}
		}

		// Invalid Tag length (!= 16 bytes)
		let invalidTagPayload = EncryptedVaultPayload(
			version: 1,
			cipher: "AES-256-GCM",
			iv: Data(count: 12).base64EncodedString(),
			ciphertext: "AA==",
			tag: Data(count: 12).base64EncodedString()
		)
		XCTAssertThrowsError(try VaultCipher.decrypt(payload: invalidTagPayload, key: keyData)) { error in
			if case .invalidFormat = (error as? VaultCipherError) {
				// expected
			} else {
				XCTFail("Expected invalidFormat, got \(error)")
			}
		}

		// Unsupported cipher
		let unsupportedCipherPayload = EncryptedVaultPayload(
			version: 1,
			cipher: "AES-128-CBC",
			iv: Data(count: 12).base64EncodedString(),
			ciphertext: "AA==",
			tag: Data(count: 16).base64EncodedString()
		)
		XCTAssertThrowsError(try VaultCipher.decrypt(payload: unsupportedCipherPayload, key: keyData)) { error in
			XCTAssertEqual(error as? VaultCipherError, .unsupportedCipher("AES-128-CBC"))
		}

		// Invalid version (< 1)
		let invalidVersionPayload = EncryptedVaultPayload(
			version: 0,
			cipher: "AES-256-GCM",
			iv: Data(count: 12).base64EncodedString(),
			ciphertext: "AA==",
			tag: Data(count: 16).base64EncodedString()
		)
		XCTAssertThrowsError(try VaultCipher.decrypt(payload: invalidVersionPayload, key: keyData)) { error in
			XCTAssertEqual(error as? VaultCipherError, .invalidVersion(0))
		}
	}
}

private extension Data {
	init(base64Encoded string: String, fatalErrorIfNil: () = ()) {
		guard let data = Data(base64Encoded: string) else {
			fatalError("Invalid base64 string: \(string)")
		}
		self = data
	}
}
