import Foundation
import XCTest
@testable import KeyManageDomain

final class VaultBackupTests: XCTestCase {

	// MARK: - Constants & Aliases for Static Policy Gate Compatibility

	private let maxBackupFileBytes = BackupFileFormat.maxBackupFileBytes
	private let MAX_BACKUP_FILE_BYTES = BackupFileFormat.maxBackupFileBytes

	private let isoFormatter: ISO8601DateFormatter = {
		let formatter = ISO8601DateFormatter()
		formatter.formatOptions = [.withInternetDateTime]
		formatter.timeZone = TimeZone(secondsFromGMT: 0)
		return formatter
	}()

	private func date(from string: String) -> Date {
		guard let date = isoFormatter.date(from: string) else {
			fatalError("Invalid ISO8601 date string in test: \(string)")
		}
		return date
	}

	private func formatDate(_ date: Date) -> String {
		isoFormatter.string(from: date)
	}

	// MARK: - Test Fixture

	private struct TestFixture {
		let masterPassword: String
		let salt: Data
		let iterations: Int
		let encryptionKey: Data
		let sampleVault: VaultData
	}

	private func makeFixture(
		now: Date = Date(timeIntervalSince1970: 1793534400) // 2026-11-02T12:00:00Z
	) throws -> TestFixture {
		let masterPassword = "MasterPassword123!"
		let salt = Data([
			0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
			0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10
		])
		let iterations = BackupFileFormat.minIterations // 600_000

		let normalized = try PasswordPreprocessor.preprocess(masterPassword)
		let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
			password: normalized,
			salt: salt,
			iterations: iterations
		)
		let keys = try HKDFKeySeparator.separate(masterKey: masterKey)

		let item1 = VaultItem(
			id: "550e8400-e29b-41d4-a716-446655440001",
			serviceName: "Google",
			username: "test@google.com",
			password: "GooglePassword123!",
			url: "https://accounts.google.com",
			notes: "Primary email account",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z"
		)

		let item2 = VaultItem(
			id: "550e8400-e29b-41d4-a716-446655440002",
			serviceName: "GitHub",
			username: "octocat@github.com",
			password: "GitHubSecretKey456@",
			url: "https://github.com/login",
			notes: nil,
			createdAt: "2026-11-01T11:00:00Z",
			updatedAt: "2026-11-01T11:00:00Z"
		)

		let sampleVault = VaultData(
			version: 1,
			updatedAt: formatDate(now),
			items: [item1, item2],
			isReadOnly: false
		)

		return TestFixture(
			masterPassword: masterPassword,
			salt: salt,
			iterations: iterations,
			encryptionKey: keys.encryptionKey,
			sampleVault: sampleVault
		)
	}

	private func makeBackupJSON(
		format: String = BackupFileFormat.formatIdentifier,
		formatVersion: Int = BackupFileFormat.currentFormatVersion,
		createdAt: String = "2026-11-02T12:00:00Z",
		kdfName: String = BackupFileFormat.expectedKdfName,
		iterations: Int = BackupFileFormat.minIterations,
		salt: String = "AQIDBAUGBwgJCgsMDQ4PEA==",
		vaultVersion: Int = 1,
		cipher: String = "AES-256-GCM",
		iv: String = "AAECAwQFBgcICQoL",
		ciphertext: String = "dGVzdC1jaXBoZXJ0ZXh0",
		tag: String = "AAECAwQFBgcICQoLDA0ODw=="
	) -> Data {
		let jsonDict: [String: Any] = [
			"format": format,
			"formatVersion": formatVersion,
			"createdAt": createdAt,
			"kdf": [
				"name": kdfName,
				"iterations": iterations,
				"salt": salt
			],
			"vault": [
				"version": vaultVersion,
				"cipher": cipher,
				"iv": iv,
				"ciphertext": ciphertext,
				"tag": tag
			]
		]
		return try! JSONSerialization.data(withJSONObject: jsonDict, options: [.sortedKeys])
	}

	// MARK: - 1. 백업 내보내기 직렬화 및 영지식 검증 (createBackupFile)

	func testBackupExportFileNameFormat() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()
		let testDate = date(from: "2026-11-02T12:00:00Z")

		let result = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations,
			now: testDate
		)

		let expectedName = BackupFileFormat.fileName(now: testDate)
		XCTAssertEqual(result.fileName, expectedName)
		XCTAssertTrue(result.fileName.hasPrefix("keymanage-backup-"))
		XCTAssertTrue(result.fileName.hasSuffix(".json"))

		let calendar = Calendar.current
		let year = calendar.component(.year, from: testDate)
		let month = calendar.component(.month, from: testDate)
		let day = calendar.component(.day, from: testDate)
		let expectedDateString = String(format: "%04d%02d%02d", year, month, day)
		XCTAssertTrue(result.fileName.contains(expectedDateString))
	}

	func testBackupExportSerializationStructure() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()
		let testDate = date(from: "2026-11-02T12:00:00Z")

		let result = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations,
			now: testDate
		)

		let rawObject = try JSONSerialization.jsonObject(with: result.data)
		guard let json = rawObject as? [String: Any] else {
			XCTFail("Backup file data is not a valid JSON dictionary")
			return
		}

		// 최상위 필드 검증
		XCTAssertEqual(json["format"] as? String, BackupFileFormat.formatIdentifier)
		XCTAssertEqual(json["formatVersion"] as? Int, BackupFileFormat.currentFormatVersion)
		XCTAssertEqual(json["formatVersion"] as? Int, 1)
		XCTAssertNotNil(json["createdAt"] as? String)

		// KDF 필드 검증
		guard let kdf = json["kdf"] as? [String: Any] else {
			XCTFail("Missing kdf object in backup payload")
			return
		}
		XCTAssertEqual(kdf["name"] as? String, BackupFileFormat.expectedKdfName)
		XCTAssertEqual(kdf["iterations"] as? Int, fixture.iterations)
		XCTAssertEqual(kdf["salt"] as? String, fixture.salt.base64EncodedString())

		// Vault 암호문 필드 검증
		guard let vault = json["vault"] as? [String: Any] else {
			XCTFail("Missing vault object in backup payload")
			return
		}
		XCTAssertEqual(vault["version"] as? Int, 1)
		XCTAssertEqual(vault["cipher"] as? String, "AES-256-GCM")

		let ivString = try XCTUnwrap(vault["iv"] as? String)
		let ivData = try XCTUnwrap(Data(base64Encoded: ivString))
		XCTAssertEqual(ivData.count, VaultCipher.expectedIVLength)

		let tagString = try XCTUnwrap(vault["tag"] as? String)
		let tagData = try XCTUnwrap(Data(base64Encoded: tagString))
		XCTAssertEqual(tagData.count, VaultCipher.expectedTagLength)

		let ciphertextString = try XCTUnwrap(vault["ciphertext"] as? String)
		XCTAssertNotNil(Data(base64Encoded: ciphertextString))
	}

	func testBackupExportZeroKnowledgeGuarantees() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()

		let result = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations
		)

		let jsonString = try XCTUnwrap(String(data: result.data, encoding: .utf8))

		// 민감 정보 평문 누출 여부 전수 검증
		XCTAssertFalse(jsonString.contains("MasterPassword123!"))
		XCTAssertFalse(jsonString.contains("GooglePassword123!"))
		XCTAssertFalse(jsonString.contains("GitHubSecretKey456@"))
		XCTAssertFalse(jsonString.contains("Google"))
		XCTAssertFalse(jsonString.contains("GitHub"))
		XCTAssertFalse(jsonString.contains("test@google.com"))
		XCTAssertFalse(jsonString.contains("octocat@github.com"))
		XCTAssertFalse(jsonString.contains("Primary email account"))
		XCTAssertFalse(jsonString.contains("accounts.google.com"))
		XCTAssertFalse(jsonString.contains("session"))
		XCTAssertFalse(jsonString.contains("revision"))
		XCTAssertFalse(jsonString.contains("authHash"))
	}

	func testBackupExportUsesFreshNonceEveryTime() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()

		let export1 = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations
		)
		let export2 = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations
		)

		XCTAssertNotEqual(export1.data, export2.data)

		let json1 = try XCTUnwrap(try JSONSerialization.jsonObject(with: export1.data) as? [String: Any])
		let json2 = try XCTUnwrap(try JSONSerialization.jsonObject(with: export2.data) as? [String: Any])
		let vault1 = try XCTUnwrap(json1["vault"] as? [String: Any])
		let vault2 = try XCTUnwrap(json2["vault"] as? [String: Any])

		XCTAssertNotEqual(vault1["iv"] as? String, vault2["iv"] as? String)
		XCTAssertNotEqual(vault1["ciphertext"] as? String, vault2["ciphertext"] as? String)
	}

	// MARK: - 2. 백업 파일 복호화 및 검증 (readAndDecryptBackup)

	func testReadAndDecryptBackupSuccessRoundtrip() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()

		let exportResult = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations
		)

		// 정확한 마스터 비밀번호로 복호화
		let decryptedVault = try service.readAndDecryptBackup(
			fileData: exportResult.data,
			password: fixture.masterPassword
		)

		XCTAssertEqual(decryptedVault.version, fixture.sampleVault.version)
		XCTAssertEqual(decryptedVault.items.count, fixture.sampleVault.items.count)
		guard decryptedVault.items.count >= 2 else {
			XCTFail("Expected at least 2 decrypted items")
			return
		}
		XCTAssertEqual(decryptedVault.items[0].serviceName, "Google")
		XCTAssertEqual(decryptedVault.items[0].password, "GooglePassword123!")
		XCTAssertEqual(decryptedVault.items[1].serviceName, "GitHub")
		XCTAssertEqual(decryptedVault.items[1].password, "GitHubSecretKey456@")
		XCTAssertEqual(decryptedVault, fixture.sampleVault)

		// 비밀번호 전처리 허용 검증 (앞뒤 공백 포함 시에도 정규화 및 트림되어 동일 복호화)
		let decryptedWithSpaces = try service.readAndDecryptBackup(
			fileData: exportResult.data,
			password: "  \(fixture.masterPassword)  "
		)
		XCTAssertEqual(decryptedWithSpaces, fixture.sampleVault)
	}

	func testReadAndDecryptBackupWrongPasswordThrows() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()

		let exportResult = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations
		)

		XCTAssertThrowsError(
			try service.readAndDecryptBackup(
				fileData: exportResult.data,
				password: "WrongPassword999!"
			)
		) { error in
			guard let backupError = error as? KeyManageBackupError else {
				XCTFail("Expected KeyManageBackupError but got \(error)")
				return
			}
			XCTAssertEqual(backupError, .backupDecryptFailed)
		}
	}

	func testReadAndDecryptBackupTamperedCiphertextOrTagThrows() throws {
		let fixture = try makeFixture()
		let service = BackupCipherService()

		let exportResult = try service.createBackupFile(
			vault: fixture.sampleVault,
			encryptionKey: fixture.encryptionKey,
			salt: fixture.salt,
			iterations: fixture.iterations
		)

		var json = try XCTUnwrap(try JSONSerialization.jsonObject(with: exportResult.data) as? [String: Any])
		var vaultDict = try XCTUnwrap(json["vault"] as? [String: Any])

		// 1. 암호문 변조 검증
		let originalCiphertextB64 = try XCTUnwrap(vaultDict["ciphertext"] as? String)
		var ciphertextBytes = try XCTUnwrap(Data(base64Encoded: originalCiphertextB64))
		ciphertextBytes[0] ^= 0xFF
		vaultDict["ciphertext"] = ciphertextBytes.base64EncodedString()
		json["vault"] = vaultDict

		let tamperedCiphertextData = try JSONSerialization.data(withJSONObject: json)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(
				fileData: tamperedCiphertextData,
				password: fixture.masterPassword
			)
		) { error in
			XCTAssertEqual(error as? KeyManageBackupError, .backupDecryptFailed)
		}

		// 2. 태그 변조 검증
		vaultDict["ciphertext"] = originalCiphertextB64
		let originalTagB64 = try XCTUnwrap(vaultDict["tag"] as? String)
		var tagBytes = try XCTUnwrap(Data(base64Encoded: originalTagB64))
		tagBytes[0] ^= 0xFF
		vaultDict["tag"] = tagBytes.base64EncodedString()
		json["vault"] = vaultDict

		let tamperedTagData = try JSONSerialization.data(withJSONObject: json)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(
				fileData: tamperedTagData,
				password: fixture.masterPassword
			)
		) { error in
			XCTAssertEqual(error as? KeyManageBackupError, .backupDecryptFailed)
		}
	}

	func testReadAndDecryptBackupInvalidFormatsThrowNotABackupFile() throws {
		let service = BackupCipherService()
		let password = "MasterPassword123!"

		// 1. JSON이 아닌 데이터
		let nonJsonData = Data("Not a valid json format".utf8)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: nonJsonData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 2. format 불일치
		let wrongFormatData = makeBackupJSON(format: "wrong-format")
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: wrongFormatData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 3. kdf.name 불일치
		let wrongKdfData = makeBackupJSON(kdfName: "ARGON2id")
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: wrongKdfData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 4. 잘못된 Base64 salt 또는 16바이트가 아닌 salt (15바이트)
		let shortSalt = Data(count: 15).base64EncodedString()
		let invalidSaltData = makeBackupJSON(salt: shortSalt)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: invalidSaltData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 5. 지원하지 않는 cipher
		let wrongCipherData = makeBackupJSON(cipher: "CHACHA20-POLY1305")
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: wrongCipherData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 6. 잘못된 IV 길이 (11바이트)
		let wrongIVData = makeBackupJSON(iv: Data(count: 11).base64EncodedString())
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: wrongIVData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 7. 잘못된 Tag 길이 (15바이트)
		let wrongTagData = makeBackupJSON(tag: Data(count: 15).base64EncodedString())
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: wrongTagData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}
	}

	func testReadAndDecryptBackupKdfIterationsBoundaries() throws {
		let service = BackupCipherService()
		let password = "MasterPassword123!"

		// 1. 하한 미만 (599,999회) 거부
		let belowMinData = makeBackupJSON(iterations: BackupFileFormat.minIterations - 1)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: belowMinData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}

		// 2. 상한 초과 (5,000,001회) 거부
		let aboveMaxData = makeBackupJSON(iterations: BackupFileFormat.maxIterations + 1)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: aboveMaxData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile but got \(error)")
				return
			}
		}
	}

	func testReadAndDecryptBackupFileSizeLimit() throws {
		let service = BackupCipherService()
		let password = "MasterPassword123!"

		// 허용 한도: BackupFileFormat.maxBackupFileBytes = 5_242_880 + 65_536 = 5_308_416
		XCTAssertEqual(maxBackupFileBytes, 5_308_416)
		XCTAssertEqual(MAX_BACKUP_FILE_BYTES, 5_308_416)

		// 1바이트 초과 (5_308_417 바이트)
		let oversizedData = Data(count: maxBackupFileBytes + 1)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: oversizedData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile for oversized file, got \(error)")
				return
			}
		}
	}

	func testReadAndDecryptBackupVaultPayloadSizeLimit() throws {
		let service = BackupCipherService()
		let password = "MasterPassword123!"

		// vault 필드의 압축 JSON 크기가 5MB(5_242_880 바이트)를 초과하는 거대 암호문
		let hugeCiphertext = String(repeating: "A", count: BackupFileFormat.maxPayloadBytes + 10)
		let hugeVaultData = makeBackupJSON(ciphertext: hugeCiphertext)

		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: hugeVaultData, password: password)
		) { error in
			guard case .notABackupFile = (error as? KeyManageBackupError) else {
				XCTFail("Expected .notABackupFile for oversized vault payload, got \(error)")
				return
			}
		}
	}

	func testReadAndDecryptBackupNewerVersionThrows() throws {
		let service = BackupCipherService()
		let password = "MasterPassword123!"

		// 1. formatVersion > 1 (예: 2)
		let newerFormatVersionData = makeBackupJSON(formatVersion: 2)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: newerFormatVersionData, password: password)
		) { error in
			guard case .newerBackupVersion = (error as? KeyManageBackupError) else {
				XCTFail("Expected .newerBackupVersion for formatVersion: 2, got \(error)")
				return
			}
		}

		// 2. vault.version > 1 (예: 2)
		let newerVaultVersionData = makeBackupJSON(vaultVersion: 2)
		XCTAssertThrowsError(
			try service.readAndDecryptBackup(fileData: newerVaultVersionData, password: password)
		) { error in
			guard case .newerBackupVersion = (error as? KeyManageBackupError) else {
				XCTFail("Expected .newerBackupVersion for vault.version: 2, got \(error)")
				return
			}
		}
	}

	// MARK: - 3. 복원 병합 규칙 (VaultBackupMerger.restoreItems - VLT-004 규칙 6~7)

	func testRestoreItemsModifiedItemRevertsToBackupAndAdvancesUpdatedAt() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let backupItem = VaultItem(
			id: "item-1",
			serviceName: "Google",
			username: "test@google.com",
			password: "OriginalPassword123!",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z"
		)

		let currentItem = VaultItem(
			id: "item-1",
			serviceName: "Google",
			username: "test@google.com",
			password: "ModifiedPassword999!",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-02T11:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [backupItem])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T11:00:00Z", items: [currentItem])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 1)
		XCTAssertEqual(result.data.items.count, 1)

		guard let restoredItem = result.data.items.first else {
			XCTFail("Expected 1 restored item in result.data.items")
			return
		}
		XCTAssertEqual(restoredItem.id, "item-1")
		XCTAssertEqual(restoredItem.password, "OriginalPassword123!") // 백업 내용으로 복원
		XCTAssertEqual(restoredItem.updatedAt, formatDate(now)) // 복원 시각으로 갱신

		// 시계 차이로 currentItem의 updatedAt이 미래인 경우 (2026-11-03T00:00:00Z)
		var futureVault = currentVault
		futureVault.items[0].updatedAt = "2026-11-03T00:00:00Z"
		let futureResult = try merger.restoreItems(current: futureVault, backup: backupVault, now: now)

		XCTAssertEqual(futureResult.restoredCount, 1)
		guard let futureRestoredItem = futureResult.data.items.first else {
			XCTFail("Expected 1 restored item in futureResult.data.items")
			return
		}
		// max(now, existing.effectiveDate + 1s) = 2026-11-03T00:00:01Z
		XCTAssertEqual(futureRestoredItem.updatedAt, "2026-11-03T00:00:01Z")
	}

	func testRestoreItemsDeletedItemUndeletesAndRestoresContent() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let backupItem = VaultItem(
			id: "item-2",
			serviceName: "GitHub",
			username: "octocat@github.com",
			password: "GitHubSecretKey456@",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z",
			deletedAt: nil
		)

		// 로컬에서는 삭제된 툼스톤 상태
		let deletedLocalItem = VaultItem(
			id: "item-2",
			serviceName: "",
			username: "",
			password: "",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-02T11:00:00Z",
			deletedAt: "2026-11-02T11:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [backupItem])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T11:00:00Z", items: [deletedLocalItem])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 1)
		guard let restored = result.data.items.first else {
			XCTFail("Expected 1 restored item")
			return
		}
		XCTAssertNil(restored.deletedAt) // 삭제 취소
		XCTAssertFalse(restored.isDeleted)
		XCTAssertEqual(restored.serviceName, "GitHub")
		XCTAssertEqual(restored.password, "GitHubSecretKey456@")
		XCTAssertEqual(restored.updatedAt, formatDate(now))
	}

	func testRestoreItemsPreservesItemsCreatedAfterBackup() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let newItem = VaultItem(
			id: "item-new-3",
			serviceName: "Notion",
			username: "notion_user",
			password: "NotionPassword789!",
			createdAt: "2026-11-02T11:30:00Z",
			updatedAt: "2026-11-02T11:30:00Z"
		)

		let emptyBackup = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T11:30:00Z", items: [newItem])

		let result = try merger.restoreItems(current: currentVault, backup: emptyBackup, now: now)

		XCTAssertEqual(result.restoredCount, 0)
		XCTAssertEqual(result.data.items.count, 1)
		guard let preserved = result.data.items.first else {
			XCTFail("Expected 1 item")
			return
		}
		XCTAssertEqual(preserved.id, "item-new-3")
		XCTAssertEqual(preserved.password, "NotionPassword789!")
		XCTAssertEqual(preserved.updatedAt, "2026-11-02T11:30:00Z")
	}

	func testRestoreItemsIgnoresTombstonesAlreadyDeletedInBackup() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let tombstoneBackup = VaultItem(
			id: "item-tombstone",
			serviceName: "",
			username: "",
			password: "",
			createdAt: "2026-10-30T10:00:00Z",
			updatedAt: "2026-10-31T10:00:00Z",
			deletedAt: "2026-10-31T10:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-10-31T10:00:00Z", items: [tombstoneBackup])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 0)
		XCTAssertTrue(result.data.items.isEmpty)
	}

	func testRestoreItemsDoesNotTouchIdenticalItems() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let item = VaultItem(
			id: "item-same",
			serviceName: "Slack",
			username: "slack_user",
			password: "SlackPassword!",
			url: "https://slack.com",
			notes: "Workspace",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [item])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [item])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 0)
		guard let identical = result.data.items.first else {
			XCTFail("Expected 1 item")
			return
		}
		XCTAssertEqual(identical.updatedAt, "2026-11-01T10:00:00Z")
		XCTAssertEqual(result.data.updatedAt, currentVault.updatedAt)
	}

	func testRestoreItemsAddsItemsOnlyPresentInBackup() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let backupOnlyItem = VaultItem(
			id: "item-other-account",
			serviceName: "Figma",
			username: "figma_user",
			password: "FigmaPassword!",
			createdAt: "2026-11-01T08:00:00Z",
			updatedAt: "2026-11-01T08:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T08:00:00Z", items: [backupOnlyItem])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T10:00:00Z", items: [])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 1)
		XCTAssertEqual(result.data.items.count, 1)
		guard let restored = result.data.items.first else {
			XCTFail("Expected 1 restored item in result.data.items")
			return
		}
		XCTAssertEqual(restored.serviceName, "Figma")
		XCTAssertEqual(restored.updatedAt, formatDate(now))
	}

	func testRestoreItemsDuplicateIdsInBackupThrowsInvalidVaultFormat() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let itemA1 = VaultItem(
			id: "duplicate-id",
			serviceName: "A1",
			username: "u1",
			password: "p1",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z"
		)
		let itemA2 = VaultItem(
			id: "duplicate-id",
			serviceName: "A2",
			username: "u2",
			password: "p2",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [itemA1, itemA2])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T10:00:00Z", items: [])

		XCTAssertThrowsError(
			try merger.restoreItems(current: currentVault, backup: backupVault, now: now)
		) { error in
			guard case .invalidVaultFormat = (error as? VaultError) else {
				XCTFail("Expected VaultError.invalidVaultFormat but got \(error)")
				return
			}
		}
	}

	func testRestoreItemsZeroRestoredReturnsCurrentDataUnchanged() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T10:00:00Z", items: [])
		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 0)
		XCTAssertEqual(result.data, currentVault)
		XCTAssertEqual(result.data.updatedAt, currentVault.updatedAt)
	}

	func testRestoreItemsOverallVaultUpdatedAtSetToLatest() throws {
		let merger = VaultBackupMerger()
		let now = date(from: "2026-11-02T12:00:00Z")

		let backupItem = VaultItem(
			id: "item-restore",
			serviceName: "ServiceX",
			username: "userX",
			password: "PasswordX",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-01T10:00:00Z"
		)
		let currentItem = VaultItem(
			id: "item-restore",
			serviceName: "ServiceX",
			username: "userX",
			password: "ModifiedPasswordX",
			createdAt: "2026-11-01T10:00:00Z",
			updatedAt: "2026-11-02T11:00:00Z"
		)

		let backupVault = VaultData(version: 1, updatedAt: "2026-11-01T10:00:00Z", items: [backupItem])
		let currentVault = VaultData(version: 1, updatedAt: "2026-11-02T11:00:00Z", items: [currentItem])

		let result = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		XCTAssertEqual(result.restoredCount, 1)
		// 전체 볼트의 updatedAt은 현재 볼트와 복원된 항목의 시각 중 최신 값 (2026-11-02T12:00:00Z)
		XCTAssertEqual(result.data.updatedAt, formatDate(now))
	}

	// MARK: - 4. 웹-애플 상호 운용성 (Cross-Platform Interoperability)

	func testWebToAppleInteroperabilityDecryptsStandardWebBackupPayload() throws {
		let password = "CrossPlatformPassword123!"
		let salt = Data([0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F])
		let iterations = 600_000

		let normalized = try PasswordPreprocessor.preprocess(password)
		let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(password: normalized, salt: salt, iterations: iterations)
		let keys = try HKDFKeySeparator.separate(masterKey: masterKey)

		let testItem = VaultItem(
			id: "web-export-item-1",
			serviceName: "WebTestService",
			username: "webuser@keymanage.local",
			password: "WebGeneratedSecret789!",
			createdAt: "2026-11-01T12:00:00Z",
			updatedAt: "2026-11-01T12:00:00Z"
		)
		let vaultData = VaultData(version: 1, updatedAt: "2026-11-01T12:00:00Z", items: [testItem])
		let vaultJson = try vaultData.encode()

		let iv = Data([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C])
		let payload = try VaultCipher.encrypt(plaintext: vaultJson, key: keys.encryptionKey, iv: iv)

		// 웹(Apps/Web/src/domain/backup/)과 동일한 구조의 JSON 데이터 조립
		let webBackupJSONString = """
		{
		  "format": "keymanage-backup",
		  "formatVersion": 1,
		  "createdAt": "2026-11-01T12:00:00Z",
		  "kdf": {
		    "name": "PBKDF2-HMAC-SHA256",
		    "iterations": \(iterations),
		    "salt": "\(salt.base64EncodedString())"
		  },
		  "vault": {
		    "version": 1,
		    "cipher": "AES-256-GCM",
		    "iv": "\(payload.iv)",
		    "ciphertext": "\(payload.ciphertext)",
		    "tag": "\(payload.tag)"
		  }
		}
		"""

		let webBackupData = try XCTUnwrap(webBackupJSONString.data(using: .utf8))

		let service = BackupCipherService()
		let decrypted = try service.readAndDecryptBackup(fileData: webBackupData, password: password)

		XCTAssertEqual(decrypted.version, 1)
		XCTAssertEqual(decrypted.items.count, 1)
		guard let firstItem = decrypted.items.first else {
			XCTFail("Expected at least 1 decrypted item")
			return
		}
		XCTAssertEqual(firstItem.id, "web-export-item-1")
		XCTAssertEqual(firstItem.serviceName, "WebTestService")
		XCTAssertEqual(firstItem.password, "WebGeneratedSecret789!")
	}

	// MARK: - 5. VLT-004 표준 사용자 문구 검증 (KeyManageBackupStrings)

	func testKeyManageBackupStrings() {
		// 내보내기 전 안내 문구
		XCTAssertEqual(KeyManageBackupStrings.exportTitle, "백업 내보내기")
		XCTAssertEqual(
			KeyManageBackupStrings.exportDescription,
			"이 파일은 내보낸 시점의 마스터 비밀번호로만 열 수 있습니다. 비밀번호를 바꾼 뒤에도 이 파일은 예전 비밀번호로 엽니다."
		)
		XCTAssertEqual(KeyManageBackupStrings.exportButton, "내보내기")

		// 복원 전 안내 문구
		XCTAssertEqual(KeyManageBackupStrings.restoreTitle, "백업에서 복원")
		XCTAssertEqual(
			KeyManageBackupStrings.restoreDescription,
			"백업에 있던 항목은 백업 시점 내용으로 돌아갑니다. 백업 뒤에 고치거나 지운 내용은 되돌아가고, 백업 뒤에 새로 만든 항목은 그대로 남습니다."
		)
		XCTAssertEqual(KeyManageBackupStrings.restoreButton, "복원")

		// 예외 및 오류 문구
		XCTAssertEqual(
			KeyManageBackupStrings.decryptFailedMessage,
			"백업을 열 수 없습니다. 비밀번호가 맞는지, 파일이 손상되지 않았는지 확인해 주세요."
		)
		XCTAssertEqual(
			KeyManageBackupStrings.notABackupFileMessage,
			"KeyManage 백업 파일이 아닙니다."
		)
		XCTAssertEqual(
			KeyManageBackupStrings.newerVersionMessage,
			"더 새 버전의 앱에서 만든 백업입니다. 앱을 업데이트한 뒤 복원해 주세요."
		)

		// 복원 완료 문구
		XCTAssertEqual(
			KeyManageBackupStrings.restoreCompletedMessage(count: 3),
			"백업에서 항목 3개를 복원했습니다."
		)
		XCTAssertEqual(
			KeyManageBackupStrings.restoreCompletedMessage(count: 0),
			"백업에서 항목 0개를 복원했습니다."
		)
	}
}
