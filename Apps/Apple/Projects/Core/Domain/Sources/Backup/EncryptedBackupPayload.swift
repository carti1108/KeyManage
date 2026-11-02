import Foundation

public enum KeyManageBackupError: LocalizedError, Equatable, Sendable {
	case notABackupFile(String)
	case newerBackupVersion(String)
	case backupDecryptFailed
	case readOnlyVault
	case sessionNotUnlocked

	public var errorDescription: String? {
		switch self {
		case .notABackupFile:
			return KeyManageBackupStrings.notABackupFileMessage
		case .newerBackupVersion:
			return KeyManageBackupStrings.newerVersionMessage
		case .backupDecryptFailed:
			return KeyManageBackupStrings.decryptFailedMessage
		case .readOnlyVault:
			return "읽기 전용 볼트에는 복원할 수 없습니다."
		case .sessionNotUnlocked:
			return "잠금 해제 상태에서만 백업 및 복원이 가능합니다."
		}
	}
}

public struct BackupKdfPayload: Codable, Equatable, Sendable {
	public let name: String
	public let iterations: Int
	public let salt: String

	public init(name: String = "PBKDF2-HMAC-SHA256", iterations: Int, salt: String) {
		self.name = name
		self.iterations = iterations
		self.salt = salt
	}
}

public struct BackupFileFormat: Codable, Equatable, Sendable {
	public static let formatIdentifier = "keymanage-backup"
	public static let currentFormatVersion = 1
	public static let maxPayloadBytes = 5_242_880
	public static let backupFileAllowanceBytes = 65_536
	public static let maxBackupFileBytes = maxPayloadBytes + backupFileAllowanceBytes
	public static let expectedKdfName = "PBKDF2-HMAC-SHA256"
	public static let minIterations = 600_000
	public static let maxIterations = 5_000_000
	public static let expectedSaltLength = 16

	public let format: String
	public let formatVersion: Int
	public let createdAt: String
	public let kdf: BackupKdfPayload
	public let vault: EncryptedVaultPayload

	public init(
		format: String = formatIdentifier,
		formatVersion: Int = currentFormatVersion,
		createdAt: String,
		kdf: BackupKdfPayload,
		vault: EncryptedVaultPayload
	) {
		self.format = format
		self.formatVersion = formatVersion
		self.createdAt = createdAt
		self.kdf = kdf
		self.vault = vault
	}

	public static func fileName(now: Date = Date(), calendar: Calendar? = nil) -> String {
		var cal = calendar ?? Calendar(identifier: .gregorian)
		cal.timeZone = TimeZone.current
		let year = cal.component(.year, from: now)
		let month = cal.component(.month, from: now)
		let day = cal.component(.day, from: now)
		return String(format: "keymanage-backup-%04d%02d%02d.json", year, month, day)
	}
}
