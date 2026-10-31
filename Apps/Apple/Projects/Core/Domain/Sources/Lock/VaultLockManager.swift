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
	func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws
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

	func unlockWithBiometrics(
		evaluatedStateHash: Data,
		recoveredEncryptionKey: Data,
		now: Date = Date()
	) throws {
		try unlockWithBiometrics(
			evaluatedStateHash: evaluatedStateHash,
			recoveredEncryptionKey: recoveredEncryptionKey,
			now: now
		)
	}
}

public final class VaultLockManager: VaultLockManagerProtocol, @unchecked Sendable {
	private let syncLock = NSRecursiveLock()

	private var _isLocked: Bool = false
	public var isLocked: Bool {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _isLocked
	}

	private var _isPrivacyCoverActive: Bool = false
	public var isPrivacyCoverActive: Bool {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _isPrivacyCoverActive
	}

	private var _autoLockTimeout: AutoLockTimeout = .default
	public var autoLockTimeout: AutoLockTimeout {
		get {
			syncLock.lock()
			defer { syncLock.unlock() }
			return _autoLockTimeout
		}
		set {
			syncLock.lock()
			defer { syncLock.unlock() }
			_autoLockTimeout = newValue
		}
	}

	private var _lastInteractionTime: Date = Date(timeIntervalSince1970: 0)
	public var lastInteractionTime: Date {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _lastInteractionTime
	}

	private var _email: String? = nil
	public var email: String? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _email
	}

	private var _sessionToken: String? = nil
	public var sessionToken: String? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _sessionToken
	}

	private var _salt: Data? = nil
	public var salt: Data? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _salt
	}

	private var _iterations: Int = 0
	public var iterations: Int {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _iterations
	}

	private var _encryptedVault: EncryptedVaultPayload? = nil
	public var encryptedVault: EncryptedVaultPayload? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _encryptedVault
	}

	private var _masterKey: Data? = nil
	public var masterKey: Data? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _masterKey
	}

	private var _encryptionKey: Data? = nil
	public var encryptionKey: Data? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _encryptionKey
	}

	private var _decryptedVault: VaultData? = nil
	public var decryptedVault: VaultData? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _decryptedVault
	}

	private var _isBiometricsEnabled: Bool = false
	public var isBiometricsEnabled: Bool {
		get {
			syncLock.lock()
			defer { syncLock.unlock() }
			return _isBiometricsEnabled
		}
		set {
			syncLock.lock()
			defer { syncLock.unlock() }
			_isBiometricsEnabled = newValue
		}
	}

	private var _lastMasterPasswordUnlockDate: Date? = nil
	public var lastMasterPasswordUnlockDate: Date? {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _lastMasterPasswordUnlockDate
	}

	private var _currentBiometryStateHash: Data? = nil
	public var currentBiometryStateHash: Data? {
		get {
			syncLock.lock()
			defer { syncLock.unlock() }
			return _currentBiometryStateHash
		}
		set {
			syncLock.lock()
			defer { syncLock.unlock() }
			_currentBiometryStateHash = newValue
		}
	}

	private var _onClipboardClearNeeded: (@Sendable () -> Void)? = nil
	public var onClipboardClearNeeded: (@Sendable () -> Void)? {
		get {
			syncLock.lock()
			defer { syncLock.unlock() }
			return _onClipboardClearNeeded
		}
		set {
			syncLock.lock()
			defer { syncLock.unlock() }
			_onClipboardClearNeeded = newValue
		}
	}

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
		syncLock.lock()
		defer { syncLock.unlock() }

		_email = email
		_sessionToken = sessionToken
		_salt = salt
		_iterations = iterations
		_encryptedVault = encryptedVault
		_isBiometricsEnabled = isBiometricsEnabled
		_currentBiometryStateHash = biometryStateHash

		_isLocked = false
		_isPrivacyCoverActive = false
		_lastInteractionTime = Date()
	}

	public func recordInteraction(at date: Date = Date()) {
		syncLock.lock()
		defer { syncLock.unlock() }
		_lastInteractionTime = date
	}

	public func checkIdleTimeout(at date: Date = Date()) -> Bool {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard !_isLocked else { return false }

		let elapsed = date.timeIntervalSince(_lastInteractionTime)
		if elapsed >= _autoLockTimeout.timeInterval {
			internalLock()
			return true
		}
		return false
	}

	public func didEnterBackground(at date: Date = Date()) {
		syncLock.lock()
		defer { syncLock.unlock() }

		_isPrivacyCoverActive = true
		if _autoLockTimeout == .immediate {
			internalLock()
		}
	}

	public func willEnterForeground(at date: Date = Date()) {
		syncLock.lock()
		defer { syncLock.unlock() }

		if !_isLocked {
			let elapsed = date.timeIntervalSince(_lastInteractionTime)
			if elapsed >= _autoLockTimeout.timeInterval {
				internalLock()
			} else {
				_isPrivacyCoverActive = false
			}
		}
	}

	public func lock() {
		syncLock.lock()
		defer { syncLock.unlock() }
		internalLock()
	}

	public func unlockWithMasterPassword(password: String, now: Date = Date()) throws {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard let salt = _salt,
		      let encryptedVault = _encryptedVault,
		      _iterations > 0 else {
			throw LockError.sessionNotInitialized
		}

		let normalized: Data
		do {
			normalized = try PasswordPreprocessor.preprocess(password)
		} catch {
			throw LockError.invalidMasterPassword
		}

		let derivedMasterKey: Data
		do {
			derivedMasterKey = try PBKDF2KeyDeriver.deriveMasterKey(
				password: normalized,
				salt: salt,
				iterations: _iterations
			)
		} catch {
			throw LockError.invalidMasterPassword
		}

		let derivedKeys: SeparatedKeys
		do {
			derivedKeys = try HKDFKeySeparator.separate(masterKey: derivedMasterKey)
		} catch {
			throw LockError.invalidMasterPassword
		}

		let decryptedData: Data
		do {
			decryptedData = try VaultCipher.decrypt(payload: encryptedVault, key: derivedKeys.encryptionKey)
		} catch {
			throw LockError.invalidMasterPassword
		}

		let decodedVault: VaultData
		do {
			decodedVault = try VaultData.decode(from: decryptedData)
		} catch {
			throw LockError.invalidMasterPassword
		}

		_decryptedVault = decodedVault
		_masterKey = derivedMasterKey
		_encryptionKey = derivedKeys.encryptionKey
		_lastMasterPasswordUnlockDate = now
		_isLocked = false
		_isPrivacyCoverActive = false
		_lastInteractionTime = now
	}

	public func unlockWithBiometrics(evaluatedStateHash: Data, now: Date = Date()) throws {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard _encryptedVault != nil else {
			throw LockError.sessionNotInitialized
		}

		guard _isBiometricsEnabled else {
			throw LockError.biometricsDisabled
		}

		guard let lastPwDate = _lastMasterPasswordUnlockDate,
		      now.timeIntervalSince(lastPwDate) <= 14 * 86400 else {
			throw LockError.biometricsExpiredRequirePassword
		}

		guard let currentHash = _currentBiometryStateHash,
		      evaluatedStateHash == currentHash else {
			throw LockError.biometricsInvalidatedRequirePassword
		}

		_isLocked = false
		_isPrivacyCoverActive = false
		_lastInteractionTime = now
	}

	public func unlockWithBiometrics(
		evaluatedStateHash: Data,
		recoveredEncryptionKey: Data,
		now: Date = Date()
	) throws {
		syncLock.lock()
		defer { syncLock.unlock() }

		// 인터페이스 스텁: 플래그 변경만 호출하고 복호화 로직은 비워둠 (RED)
		try unlockWithBiometrics(evaluatedStateHash: evaluatedStateHash, now: now)
	}

	public func logout() {
		syncLock.lock()
		defer { syncLock.unlock() }

		internalLock()

		_email = nil
		_sessionToken = nil
		_salt = nil
		_iterations = 0
		_encryptedVault = nil
		_isBiometricsEnabled = false
		_currentBiometryStateHash = nil
		_lastMasterPasswordUnlockDate = nil
		_isPrivacyCoverActive = false
	}

	private func internalLock() {
		_isLocked = true

		zeroizeData(&_masterKey)
		zeroizeData(&_encryptionKey)
		_decryptedVault = nil

		_onClipboardClearNeeded?()
	}

	private func zeroizeData(_ data: inout Data?) {
		guard data != nil else { return }
		data?.withUnsafeMutableBytes { ptr in
			if let base = ptr.baseAddress {
				memset_s(base, ptr.count, 0, ptr.count)
			}
		}
		data = nil
	}
}
