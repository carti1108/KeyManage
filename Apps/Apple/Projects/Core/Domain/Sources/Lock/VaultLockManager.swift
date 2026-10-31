import Foundation

public protocol VaultLockManagerProtocol: AnyObject, Sendable {
	var isLocked: Bool { get }
	var isPrivacyCoverActive: Bool { get }
	var autoLockTimeout: AutoLockTimeout { get set }
	var lastInteractionTime: Date { get }

	// 세션 보존 데이터 (잠금 중 유지)
	var email: String? { get }
	var sessionToken: String? { get }
	var salt: Data? { get }
	var iterations: Int { get }
	var encryptedVault: EncryptedVaultPayload? { get }

	// 민감 자격증명 (잠금 시 소거 대상)
	var masterKey: Data? { get }
	var encryptionKey: Data? { get }
	var decryptedVault: VaultData? { get }

	// 생체 인증 상태
	var isBiometricsEnabled: Bool { get set }
	var lastMasterPasswordUnlockDate: Date? { get }
	var currentBiometryStateHash: Data? { get set }

	// 이벤트 콜백
	var onClipboardClearNeeded: (@Sendable () -> Void)? { get set }

	func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool,
		biometryStateHash: Data?
	)
	func recordInteraction(at date: Date)
	func checkIdleTimeout(at date: Date) -> Bool
	func didEnterBackground(at date: Date)
	func willEnterForeground(at date: Date)
	func lock()
	func unlockWithMasterPassword(password: String, now: Date) throws
	func unlockWithBiometrics(evaluatedStateHash: Data, now: Date) throws
	func logout()
}

public extension VaultLockManagerProtocol {
	func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool = false,
		biometryStateHash: Data? = nil
	) {
		setupSession(
			email: email,
			sessionToken: sessionToken,
			salt: salt,
			iterations: iterations,
			encryptedVault: encryptedVault,
			isBiometricsEnabled: isBiometricsEnabled,
			biometryStateHash: biometryStateHash
		)
	}
}

public final class VaultLockManager: VaultLockManagerProtocol, @unchecked Sendable {
	public private(set) var isLocked: Bool = false
	public private(set) var isPrivacyCoverActive: Bool = false
	public var autoLockTimeout: AutoLockTimeout = .default
	public private(set) var lastInteractionTime: Date = Date(timeIntervalSince1970: 0)

	public private(set) var email: String? = nil
	public private(set) var sessionToken: String? = nil
	public private(set) var salt: Data? = nil
	public private(set) var iterations: Int = 0
	public private(set) var encryptedVault: EncryptedVaultPayload? = nil

	public private(set) var masterKey: Data? = nil
	public private(set) var encryptionKey: Data? = nil
	public private(set) var decryptedVault: VaultData? = nil

	public var isBiometricsEnabled: Bool = false
	public private(set) var lastMasterPasswordUnlockDate: Date? = nil
	public var currentBiometryStateHash: Data? = nil

	public var onClipboardClearNeeded: (@Sendable () -> Void)? = nil

	public init() {}

	public func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool = false,
		biometryStateHash: Data? = nil
	) {
		// Stub: 비즈니스 구현 없음
	}

	public func recordInteraction(at date: Date) {
		// Stub: 비즈니스 구현 없음
	}

	public func checkIdleTimeout(at date: Date) -> Bool {
		// Stub: 비즈니스 구현 없음
		return false
	}

	public func didEnterBackground(at date: Date) {
		// Stub: 비즈니스 구현 없음
	}

	public func willEnterForeground(at date: Date) {
		// Stub: 비즈니스 구현 없음
	}

	public func lock() {
		// Stub: 비즈니스 구현 없음
	}

	public func unlockWithMasterPassword(password: String, now: Date) throws {
		// Stub: 비즈니스 구현 없음
		throw LockError.notImplemented
	}

	public func unlockWithBiometrics(evaluatedStateHash: Data, now: Date) throws {
		// Stub: 비즈니스 구현 없음
		throw LockError.notImplemented
	}

	public func logout() {
		// Stub: 비즈니스 구현 없음
	}
}
