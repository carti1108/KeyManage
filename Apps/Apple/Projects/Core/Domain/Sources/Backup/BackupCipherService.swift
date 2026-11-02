import Foundation

public struct BackupCipherService: Sendable {
	public init() {}

	public func createBackupFile(
		vault: VaultData,
		encryptionKey: Data,
		salt: Data,
		iterations: Int,
		now: Date = Date()
	) throws -> (fileName: String, data: Data) {
		let fileName = BackupFileFormat.fileName(now: now)
		var vaultEncoded = try vault.encode()
		defer { zeroizeData(&vaultEncoded) }

		let encryptedPayload = try VaultCipher.encrypt(
			plaintext: vaultEncoded,
			key: encryptionKey
		)

		let kdfPayload = BackupKdfPayload(
			name: BackupFileFormat.expectedKdfName,
			iterations: iterations,
			salt: salt.base64EncodedString()
		)

		let backupFormat = BackupFileFormat(
			format: BackupFileFormat.formatIdentifier,
			formatVersion: BackupFileFormat.currentFormatVersion,
			createdAt: VaultDateHelper.format(now),
			kdf: kdfPayload,
			vault: encryptedPayload
		)

		let encoder = JSONEncoder()
		encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
		let encodedData = try encoder.encode(backupFormat)

		return (fileName: fileName, data: encodedData)
	}

	public func readAndDecryptBackup(
		fileData: Data,
		password: String
	) throws -> VaultData {
		// 1. 전체 파일 크기 검증
		guard fileData.count <= BackupFileFormat.maxBackupFileBytes else {
			throw KeyManageBackupError.notABackupFile("파일 크기(\(fileData.count)B)가 최대 한도(\(BackupFileFormat.maxBackupFileBytes)B)를 초과했습니다.")
		}

		// 2. JSON 객체 파싱
		let rawJson: Any
		do {
			rawJson = try JSONSerialization.jsonObject(with: fileData, options: [])
		} catch {
			throw KeyManageBackupError.notABackupFile("JSON 파싱 실패: \(error.localizedDescription)")
		}

		guard let dict = rawJson as? [String: Any] else {
			throw KeyManageBackupError.notABackupFile("루트가 JSON 객체가 아닙니다.")
		}

		// 3. format 필드 검증
		guard let format = dict["format"] as? String, format == BackupFileFormat.formatIdentifier else {
			throw KeyManageBackupError.notABackupFile("형식 식별자가 올바르지 않습니다.")
		}

		// 4. formatVersion 검증
		guard let formatVersion = dict["formatVersion"] as? Int, formatVersion >= 1 else {
			throw KeyManageBackupError.notABackupFile("formatVersion이 유효한 양의 정수가 아닙니다.")
		}
		guard formatVersion <= BackupFileFormat.currentFormatVersion else {
			throw KeyManageBackupError.newerBackupVersion("지원하지 않는 상위 백업 포맷 버전입니다: \(formatVersion)")
		}

		// 5. kdf 필드 검증
		guard let kdfDict = dict["kdf"] as? [String: Any] else {
			throw KeyManageBackupError.notABackupFile("kdf 필드가 객체가 아닙니다.")
		}
		guard let kdfName = kdfDict["name"] as? String, kdfName == BackupFileFormat.expectedKdfName else {
			throw KeyManageBackupError.notABackupFile("지원하지 않는 KDF 알고리즘입니다.")
		}
		guard let iterations = kdfDict["iterations"] as? Int else {
			throw KeyManageBackupError.notABackupFile("kdf.iterations가 정수가 아닙니다.")
		}
		guard iterations >= BackupFileFormat.minIterations && iterations <= BackupFileFormat.maxIterations else {
			throw KeyManageBackupError.notABackupFile("kdf.iterations(\(iterations))가 허용 범위(\(BackupFileFormat.minIterations)~\(BackupFileFormat.maxIterations))를 벗어났습니다.")
		}
		guard let saltString = kdfDict["salt"] as? String,
		      let saltData = Data(base64Encoded: saltString),
		      saltData.count == BackupFileFormat.expectedSaltLength else {
			throw KeyManageBackupError.notABackupFile("kdf.salt가 16바이트 Base64 문자열이 아닙니다.")
		}

		// 6. vault 필드 검증
		guard let vaultDict = dict["vault"] as? [String: Any] else {
			throw KeyManageBackupError.notABackupFile("vault 필드가 객체가 아닙니다.")
		}

		// vault 필드 압축 JSON 크기 검증
		let compactVaultData: Data
		do {
			compactVaultData = try JSONSerialization.data(withJSONObject: vaultDict, options: [])
		} catch {
			throw KeyManageBackupError.notABackupFile("vault 필드 직렬화 실패: \(error.localizedDescription)")
		}
		guard compactVaultData.count <= BackupFileFormat.maxPayloadBytes else {
			throw KeyManageBackupError.notABackupFile("vault 페이로드 크기(\(compactVaultData.count)B)가 5MB를 초과했습니다.")
		}

		guard let vaultVersion = vaultDict["version"] as? Int, vaultVersion >= 1 else {
			throw KeyManageBackupError.notABackupFile("vault.version이 유효한 양의 정수가 아닙니다.")
		}
		guard vaultVersion <= 1 else {
			throw KeyManageBackupError.newerBackupVersion("지원하지 않는 상위 볼트 암호화 버전입니다: \(vaultVersion)")
		}

		guard let cipher = vaultDict["cipher"] as? String, cipher == VaultCipher.supportedCipher else {
			throw KeyManageBackupError.notABackupFile("지원하지 않는 암호화 알고리즘입니다: \(vaultDict["cipher"] ?? "nil")")
		}

		guard let ivString = vaultDict["iv"] as? String,
		      let ivData = Data(base64Encoded: ivString),
		      ivData.count == VaultCipher.expectedIVLength else {
			throw KeyManageBackupError.notABackupFile("vault.iv가 12바이트 Base64 문자열이 아닙니다.")
		}

		guard let tagString = vaultDict["tag"] as? String,
		      let tagData = Data(base64Encoded: tagString),
		      tagData.count == VaultCipher.expectedTagLength else {
			throw KeyManageBackupError.notABackupFile("vault.tag가 16바이트 Base64 문자열이 아닙니다.")
		}

		guard let ciphertextString = vaultDict["ciphertext"] as? String,
		      Data(base64Encoded: ciphertextString) != nil else {
			throw KeyManageBackupError.notABackupFile("vault.ciphertext가 Base64 문자열이 아닙니다.")
		}

		let encryptedVaultPayload = EncryptedVaultPayload(
			version: vaultVersion,
			cipher: cipher,
			iv: ivString,
			ciphertext: ciphertextString,
			tag: tagString
		)

		// 7. 키 유도
		var normalizedPasswordData: Data
		do {
			normalizedPasswordData = try PasswordPreprocessor.preprocess(password)
		} catch {
			throw KeyManageBackupError.backupDecryptFailed
		}
		defer { zeroizeData(&normalizedPasswordData) }

		var masterKey: Data
		do {
			masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
				password: normalizedPasswordData,
				salt: saltData,
				iterations: iterations
			)
		} catch {
			throw KeyManageBackupError.backupDecryptFailed
		}
		defer { zeroizeData(&masterKey) }

		var encryptionKey: Data
		do {
			encryptionKey = try HKDFKeySeparator.deriveEncryptionKey(masterKey: masterKey)
		} catch {
			throw KeyManageBackupError.backupDecryptFailed
		}
		defer { zeroizeData(&encryptionKey) }

		// 8. 복호화
		var decryptedData: Data
		do {
			decryptedData = try VaultCipher.decrypt(
				payload: encryptedVaultPayload,
				key: encryptionKey
			)
		} catch {
			throw KeyManageBackupError.backupDecryptFailed
		}
		defer { zeroizeData(&decryptedData) }

		// 9. 볼트 스키마 버전 검사 및 파싱
		if let rawDecryptedJson = try? JSONSerialization.jsonObject(with: decryptedData, options: []) as? [String: Any],
		   let schemaVersion = rawDecryptedJson["version"] as? Int,
		   schemaVersion > VaultData.currentVersion {
			throw KeyManageBackupError.newerBackupVersion("모르는 볼트 스키마 버전입니다: \(schemaVersion)")
		}

		do {
			return try VaultData.decode(from: decryptedData)
		} catch let err as VaultError {
			throw err
		} catch {
			throw VaultError.invalidVaultFormat(error.localizedDescription)
		}
	}

	private func zeroizeData(_ data: inout Data) {
		guard !data.isEmpty else { return }
		data.withUnsafeMutableBytes { ptr in
			if let base = ptr.baseAddress {
				memset_s(base, ptr.count, 0, ptr.count)
			}
		}
		data.removeAll(keepingCapacity: false)
	}
}
