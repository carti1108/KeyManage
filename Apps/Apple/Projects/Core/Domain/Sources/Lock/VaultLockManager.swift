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
	var revision: Int { get }

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
		biometryStateHash: Data?,
		revision: Int
	)
	func recordInteraction(at date: Date)
	func checkIdleTimeout(at date: Date) -> Bool
	func didEnterBackground(at date: Date)
	func willEnterForeground(at date: Date)
	func lock()
	func unlockWithMasterPassword(password: String, now: Date) throws
	func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws
	func logout()

	// VLT-004 암호화 백업 내보내기와 복원
	func exportBackup(now: Date) throws -> (fileName: String, data: Data)
	func restoreBackup(fileData: Data, password: String, now: Date) throws -> (restoredCount: Int, newVault: VaultData)

	// SEC-004 마스터 비밀번호 변경
	func changeMasterPassword(
		currentPassword: String,
		newPassword: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date
	) async throws

	// SEC-003 규칙 11 서버 KDF 변경 감지
	func checkIfPasswordChangedOnServer(
		networkClient: AuthNetworkClientProtocol
	) async throws -> Bool

	// VLT-001 볼트 아이템 추가, 수정, 삭제 (CRUD)
	@discardableResult
	func addVaultItem(
		serviceName: String,
		username: String,
		password: String,
		url: String?,
		notes: String?,
		now: Date
	) throws -> VaultItem

	func updateVaultItem(
		item: VaultItem,
		now: Date
	) throws

	func deleteVaultItem(
		id: String,
		now: Date
	) throws
}

public extension VaultLockManagerProtocol {

	@discardableResult
	func addVaultItem(
		serviceName: String,
		username: String = "",
		password: String = "",
		url: String? = nil,
		notes: String? = nil,
		now: Date = Date()
	) throws -> VaultItem {
		try addVaultItem(serviceName: serviceName, username: username, password: password, url: url, notes: notes, now: now)
	}

	func updateVaultItem(
		item: VaultItem,
		now: Date = Date()
	) throws {
		try updateVaultItem(item: item, now: now)
	}

	func deleteVaultItem(
		id: String,
		now: Date = Date()
	) throws {
		try deleteVaultItem(id: id, now: now)
	}
	func changeMasterPassword(
		currentPassword: String,
		newPassword: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date = Date()
	) async throws {
		try await changeMasterPassword(
			currentPassword: currentPassword,
			newPassword: newPassword,
			networkClient: networkClient,
			now: now
		)
	}

	func exportBackup(now: Date = Date()) throws -> (fileName: String, data: Data) {
		try exportBackup(now: now)
	}

	func restoreBackup(fileData: Data, password: String, now: Date = Date()) throws -> (restoredCount: Int, newVault: VaultData) {
		try restoreBackup(fileData: fileData, password: password, now: now)
	}

	func lockVault() {
		lock()
	}

	func setupSession(
		email: String,
		sessionToken: String,
		salt: Data,
		iterations: Int,
		encryptedVault: EncryptedVaultPayload,
		isBiometricsEnabled: Bool = false,
		biometryStateHash: Data? = nil,
		revision: Int = 1
	) {
		setupSession(
			email: email,
			sessionToken: sessionToken,
			salt: salt,
			iterations: iterations,
			encryptedVault: encryptedVault,
			isBiometricsEnabled: isBiometricsEnabled,
			biometryStateHash: biometryStateHash,
			revision: revision
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

	private var _revision: Int = 1
	public var revision: Int {
		syncLock.lock()
		defer { syncLock.unlock() }
		return _revision
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
		biometryStateHash: Data? = nil,
		revision: Int = 1
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
		_revision = revision

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
		guard !_isLocked, _autoLockTimeout != .immediate else {
			syncLock.unlock()
			return false
		}

		let elapsed = date.timeIntervalSince(_lastInteractionTime)
		let shouldLock = elapsed >= _autoLockTimeout.timeInterval
		syncLock.unlock()

		if shouldLock {
			internalLock()
			return true
		}
		return false
	}

	public func didEnterBackground(at date: Date = Date()) {
		syncLock.lock()
		_isPrivacyCoverActive = true
		let shouldLock = (_autoLockTimeout == .immediate)
		syncLock.unlock()

		if shouldLock {
			internalLock()
		}
	}

	public func willEnterForeground(at date: Date = Date()) {
		syncLock.lock()
		_isPrivacyCoverActive = false

		let shouldLock: Bool
		if !_isLocked {
			let elapsed = date.timeIntervalSince(_lastInteractionTime)
			shouldLock = (elapsed >= _autoLockTimeout.timeInterval)
		} else {
			shouldLock = false
		}
		syncLock.unlock()

		if shouldLock {
			internalLock()
		}
	}

	public func lock() {
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

		var normalized: Data?
		defer { zeroizeData(&normalized) }
		do {
			normalized = try PasswordPreprocessor.preprocess(password)
		} catch {
			throw LockError.invalidMasterPassword
		}

		var derivedMasterKey: Data?
		do {
			derivedMasterKey = try PBKDF2KeyDeriver.deriveMasterKey(
				password: normalized ?? Data(),
				salt: salt,
				iterations: _iterations
			)
		} catch {
			zeroizeData(&derivedMasterKey)
			throw LockError.invalidMasterPassword
		}

		let derivedKeys: SeparatedKeys
		do {
			derivedKeys = try HKDFKeySeparator.separate(masterKey: derivedMasterKey ?? Data())
		} catch {
			zeroizeData(&derivedMasterKey)
			throw LockError.invalidMasterPassword
		}
		var tempAuthKey: Data? = derivedKeys.authHash
		var tempEncKey: Data? = derivedKeys.encryptionKey
		defer { zeroizeData(&tempAuthKey) }

		var decryptedData: Data?
		defer { zeroizeData(&decryptedData) }
		do {
			decryptedData = try VaultCipher.decrypt(
				payload: encryptedVault,
				key: tempEncKey ?? Data()
			)
		} catch {
			zeroizeData(&derivedMasterKey)
			zeroizeData(&tempEncKey)
			throw LockError.invalidMasterPassword
		}

		let decodedVault: VaultData
		do {
			decodedVault = try VaultData.decode(from: decryptedData ?? Data())
		} catch {
			zeroizeData(&derivedMasterKey)
			zeroizeData(&tempEncKey)
			throw LockError.invalidMasterPassword
		}

		zeroizeData(&_masterKey)
		zeroizeData(&_encryptionKey)
		_decryptedVault = decodedVault
		_masterKey = derivedMasterKey
		_encryptionKey = tempEncKey
		_lastMasterPasswordUnlockDate = now
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

		guard let encryptedVault = _encryptedVault else {
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

		var decryptedData: Data?
		defer { zeroizeData(&decryptedData) }
		let decodedVault: VaultData
		do {
			let plain = try VaultCipher.decrypt(payload: encryptedVault, key: recoveredEncryptionKey)
			decryptedData = plain
			decodedVault = try VaultData.decode(from: plain)
		} catch {
			throw LockError.invalidMasterPassword
		}

		zeroizeData(&_encryptionKey)
		_decryptedVault = decodedVault
		_encryptionKey = recoveredEncryptionKey
		_isLocked = false
		_isPrivacyCoverActive = false
		_lastInteractionTime = now
	}

	public func logout() {
		internalLock()

		syncLock.lock()
		defer { syncLock.unlock() }

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

	public func exportBackup(now: Date = Date()) throws -> (fileName: String, data: Data) {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard !_isLocked,
		      let vault = _decryptedVault,
		      let encKey = _encryptionKey,
		      let salt = _salt,
		      _iterations > 0 else {
			throw KeyManageBackupError.sessionNotUnlocked
		}

		let service = BackupCipherService()
		return try service.createBackupFile(
			vault: vault,
			encryptionKey: encKey,
			salt: salt,
			iterations: _iterations,
			now: now
		)
	}

	public func restoreBackup(
		fileData: Data,
		password: String,
		now: Date = Date()
	) throws -> (restoredCount: Int, newVault: VaultData) {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard !_isLocked,
		      let currentVault = _decryptedVault,
		      let encKey = _encryptionKey else {
			throw KeyManageBackupError.sessionNotUnlocked
		}

		guard !currentVault.isReadOnly else {
			throw KeyManageBackupError.readOnlyVault
		}

		let cipherService = BackupCipherService()
		let backupVault = try cipherService.readAndDecryptBackup(fileData: fileData, password: password)

		let merger = VaultBackupMerger()
		let (mergedVault, restoredCount) = try merger.restoreItems(current: currentVault, backup: backupVault, now: now)

		if restoredCount > 0 {
			let encodedMerged = try mergedVault.encode()
			guard encodedMerged.count <= BackupFileFormat.maxPayloadBytes else {
				throw KeyManageBackupError.notABackupFile("복원 결과 볼트 크기(\(encodedMerged.count)B)가 5MB 한도를 초과했습니다.")
			}
			_decryptedVault = mergedVault
			let reencrypted = try VaultCipher.encrypt(
				plaintext: encodedMerged,
				key: encKey
			)
			_encryptedVault = reencrypted
			_lastInteractionTime = now
		}

		return (restoredCount: restoredCount, newVault: mergedVault)
	}

	// MARK: - SEC-004 마스터 비밀번호 변경

	private struct SessionCredentialsSnapshot {
		let isLocked: Bool
		let salt: Data?
		let sessionToken: String?
		let iterations: Int
		let email: String?
		let decryptedVault: VaultData?
		let encryptedVault: EncryptedVaultPayload?
		let revision: Int
	}

	private func getCredentialsSnapshot() -> SessionCredentialsSnapshot {
		syncLock.lock()
		defer { syncLock.unlock() }
		return SessionCredentialsSnapshot(
			isLocked: _isLocked,
			salt: _salt,
			sessionToken: _sessionToken,
			iterations: _iterations,
			email: _email,
			decryptedVault: _decryptedVault,
			encryptedVault: _encryptedVault,
			revision: _revision
		)
	}

	private func applyChangedPassword(
		sessionToken: String,
		salt: Data,
		encryptedVault: EncryptedVaultPayload,
		masterKey: Data,
		encryptionKey: Data,
		revision: Int,
		now: Date
	) {
		syncLock.lock()
		defer { syncLock.unlock() }

		// 1. 비동기 대기 중 사용자가 로그아웃한 경우 자격증명 폐기
		guard _email != nil else {
			var mk: Data? = masterKey
			var ek: Data? = encryptionKey
			zeroizeData(&mk)
			zeroizeData(&ek)
			return
		}

		_sessionToken = sessionToken
		_salt = salt
		_encryptedVault = encryptedVault
		_revision = revision
		_lastMasterPasswordUnlockDate = now
		_lastInteractionTime = now

		zeroizeData(&_masterKey)
		zeroizeData(&_encryptionKey)

		// 2. 잠금 해제 상태가 유지된 경우에만 메모리에 보존, 대기 중 잠긴 경우 즉시 소거
		if !_isLocked {
			_masterKey = masterKey
			_encryptionKey = encryptionKey
		} else {
			var mk: Data? = masterKey
			var ek: Data? = encryptionKey
			zeroizeData(&mk)
			zeroizeData(&ek)
		}
	}

	private func getKdfCheckInfo() -> (email: String, saltBase64: String, iterations: Int)? {
		syncLock.lock()
		defer { syncLock.unlock() }
		guard let email = _email, let salt = _salt, _iterations > 0 else {
			return nil
		}
		return (email: email, saltBase64: salt.base64EncodedString(), iterations: _iterations)
	}

	public func changeMasterPassword(
		currentPassword: String,
		newPassword: String,
		networkClient: AuthNetworkClientProtocol,
		now: Date = Date()
	) async throws {
		let snapshot = getCredentialsSnapshot()
		guard !snapshot.isLocked,
		      let currentSalt = snapshot.salt,
		      let sessionToken = snapshot.sessionToken,
		      snapshot.iterations > 0,
		      let decryptedVault = snapshot.decryptedVault else {
			throw MasterPasswordChangeError.offline
		}

		if decryptedVault.isReadOnly {
			throw MasterPasswordChangeError.readOnlyVault
		}

		// 1. 현재 비밀번호 검증
		var normalizedCurrent: Data?
		defer { zeroizeData(&normalizedCurrent) }
		do {
			normalizedCurrent = try PasswordPreprocessor.preprocess(currentPassword)
		} catch {
			throw MasterPasswordChangeError.invalidCurrentPassword
		}

		var derivedCurrentMasterKey: Data?
		defer { zeroizeData(&derivedCurrentMasterKey) }
		do {
			derivedCurrentMasterKey = try PBKDF2KeyDeriver.deriveMasterKey(
				password: normalizedCurrent ?? Data(),
				salt: currentSalt,
				iterations: snapshot.iterations
			)
		} catch {
			throw MasterPasswordChangeError.invalidCurrentPassword
		}

		let currentKeys: SeparatedKeys
		do {
			currentKeys = try HKDFKeySeparator.separate(masterKey: derivedCurrentMasterKey ?? Data())
		} catch {
			throw MasterPasswordChangeError.invalidCurrentPassword
		}
		var currentAuthKey: Data? = currentKeys.authHash
		var currentEncKey: Data? = currentKeys.encryptionKey
		defer {
			zeroizeData(&currentAuthKey)
			zeroizeData(&currentEncKey)
		}

		if let encVault = snapshot.encryptedVault {
			do {
				_ = try VaultCipher.decrypt(payload: encVault, key: currentEncKey ?? Data())
			} catch {
				throw MasterPasswordChangeError.invalidCurrentPassword
			}
		}

		// 2. 새 비밀번호 검증 (SEC-002, SEC-004 규칙 2)
		if newPassword.count < 12 {
			throw MasterPasswordChangeError.tooShort
		}
		if newPassword == currentPassword {
			throw MasterPasswordChangeError.samePassword
		}

		var normalizedNew: Data?
		defer { zeroizeData(&normalizedNew) }
		do {
			normalizedNew = try PasswordPreprocessor.preprocess(newPassword)
		} catch {
			throw MasterPasswordChangeError.other("새 마스터 비밀번호에 유효하지 않은 문자가 포함되어 있습니다.")
		}

		if let normNew = normalizedNew, let normCur = normalizedCurrent, normNew == normCur {
			throw MasterPasswordChangeError.samePassword
		}

		// 3. 새 16바이트 salt 생성
		var newSalt = Data(count: 16)
		let saltResult = newSalt.withUnsafeMutableBytes { bytes in
			SecRandomCopyBytes(kSecRandomDefault, 16, bytes.baseAddress!)
		}
		guard saltResult == errSecSuccess else {
			throw MasterPasswordChangeError.other("새 salt 생성에 실패했습니다.")
		}

		// 4. 새 마스터 키 유도 및 키 분리
		var newDerivedMasterKey: Data?
		var newEncKey: Data?
		defer {
			zeroizeData(&newDerivedMasterKey)
			zeroizeData(&newEncKey)
		}

		do {
			newDerivedMasterKey = try PBKDF2KeyDeriver.deriveMasterKey(
				password: normalizedNew ?? Data(),
				salt: newSalt,
				iterations: snapshot.iterations
			)
		} catch {
			throw MasterPasswordChangeError.other("새 마스터 키 유도에 실패했습니다.")
		}

		let newKeys: SeparatedKeys
		do {
			newKeys = try HKDFKeySeparator.separate(masterKey: newDerivedMasterKey ?? Data())
		} catch {
			throw MasterPasswordChangeError.other("새 키 분리에 실패했습니다.")
		}
		var newAuthKey: Data? = newKeys.authHash
		newEncKey = newKeys.encryptionKey
		defer {
			zeroizeData(&newAuthKey)
		}

		// 5. 볼트 재암호화
		let encodedVault = try decryptedVault.encode()
		let newEncryptedVault = try VaultCipher.encrypt(plaintext: encodedVault, key: newEncKey ?? Data())
		guard newEncryptedVault.ciphertext.count <= BackupFileFormat.maxPayloadBytes else {
			throw MasterPasswordChangeError.vaultTooLarge
		}

		// 6. 서버 요청 (POST /api/v1/auth/change-password)
		let requestPayload = ChangePasswordRequestPayload(
			currentAuthHash: currentAuthKey?.base64EncodedString() ?? "",
			newAuthHash: newAuthKey?.base64EncodedString() ?? "",
			kdf: .init(iterations: snapshot.iterations, salt: newSalt.base64EncodedString()),
			vault: .init(expectedRevision: snapshot.revision, payload: newEncryptedVault)
		)

		let response: ChangePasswordResponsePayload
		response = try await networkClient.changePassword(
			accessToken: sessionToken,
			request: requestPayload
		)

		// 7. 자격증명 원자적 갱신
		applyChangedPassword(
			sessionToken: response.accessToken,
			salt: newSalt,
			encryptedVault: newEncryptedVault,
			masterKey: newDerivedMasterKey ?? Data(),
			encryptionKey: newEncKey ?? Data(),
			revision: response.revision,
			now: now
		)
	}

	// MARK: - SEC-003 규칙 11 서버 KDF 변경 감지

	public func checkIfPasswordChangedOnServer(
		networkClient: AuthNetworkClientProtocol
	) async throws -> Bool {
		guard let info = getKdfCheckInfo() else {
			return false
		}
		let preloginResp = try await networkClient.prelogin(email: info.email)
		let differs = (preloginResp.salt != info.saltBase64 || preloginResp.iterations != info.iterations)
		return differs
	}

	// MARK: - VLT-001 볼트 아이템 추가, 수정, 삭제 (CRUD)

	@discardableResult
	public func addVaultItem(
		serviceName: String,
		username: String,
		password: String,
		url: String?,
		notes: String?,
		now: Date
	) throws -> VaultItem {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard !_isLocked, var vault = _decryptedVault, let encKey = _encryptionKey else {
			throw VaultItemError.locked
		}
		guard !vault.isReadOnly else {
			throw VaultItemError.vaultIsReadOnly
		}

		let trimmedServiceName = serviceName.trimmingCharacters(in: .whitespacesAndNewlines)
		guard !trimmedServiceName.isEmpty else {
			throw VaultItemError.emptyServiceName
		}

		let timeString = VaultDateHelper.format(now)
		let newItem = VaultItem(
			id: UUID().uuidString,
			serviceName: trimmedServiceName,
			username: username,
			password: password,
			url: url?.isEmpty == true ? nil : url,
			notes: notes?.isEmpty == true ? nil : notes,
			createdAt: timeString,
			updatedAt: timeString
		)

		try vault.addItem(newItem, now: now)

		let encoded = try vault.encode()
		guard encoded.count <= BackupFileFormat.maxPayloadBytes else {
			throw VaultItemError.vaultTooLarge
		}

		let newEncrypted = try VaultCipher.encrypt(plaintext: encoded, key: encKey)
		_decryptedVault = vault
		_encryptedVault = newEncrypted
		_lastInteractionTime = now
		return newItem
	}

	public func updateVaultItem(
		item: VaultItem,
		now: Date
	) throws {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard !_isLocked, var vault = _decryptedVault, let encKey = _encryptionKey else {
			throw VaultItemError.locked
		}
		guard !vault.isReadOnly else {
			throw VaultItemError.vaultIsReadOnly
		}

		let trimmedServiceName = item.serviceName.trimmingCharacters(in: .whitespacesAndNewlines)
		guard !trimmedServiceName.isEmpty else {
			throw VaultItemError.emptyServiceName
		}

		guard vault.items.contains(where: { $0.id == item.id && !$0.isDeleted }) else {
			throw VaultItemError.itemNotFound
		}

		var updated = item
		updated.serviceName = trimmedServiceName
		if let url = updated.url, url.isEmpty {
			updated.url = nil
		}
		if let notes = updated.notes, notes.isEmpty {
			updated.notes = nil
		}

		try vault.updateItem(updated, now: now)

		let encoded = try vault.encode()
		guard encoded.count <= BackupFileFormat.maxPayloadBytes else {
			throw VaultItemError.vaultTooLarge
		}

		let newEncrypted = try VaultCipher.encrypt(plaintext: encoded, key: encKey)
		_decryptedVault = vault
		_encryptedVault = newEncrypted
		_lastInteractionTime = now
	}

	public func deleteVaultItem(
		id: String,
		now: Date
	) throws {
		syncLock.lock()
		defer { syncLock.unlock() }

		guard !_isLocked, var vault = _decryptedVault, let encKey = _encryptionKey else {
			throw VaultItemError.locked
		}
		guard !vault.isReadOnly else {
			throw VaultItemError.vaultIsReadOnly
		}

		guard vault.items.contains(where: { $0.id == id && !$0.isDeleted }) else {
			throw VaultItemError.itemNotFound
		}

		try vault.deleteItem(id: id, now: now)

		let encoded = try vault.encode()
		guard encoded.count <= BackupFileFormat.maxPayloadBytes else {
			throw VaultItemError.vaultTooLarge
		}

		let newEncrypted = try VaultCipher.encrypt(plaintext: encoded, key: encKey)
		_decryptedVault = vault
		_encryptedVault = newEncrypted
		_lastInteractionTime = now
	}

	private func internalLock() {
		let callback: (@Sendable () -> Void)?
		syncLock.lock()
		_isLocked = true

		zeroizeData(&_masterKey)
		zeroizeData(&_encryptionKey)
		_decryptedVault = nil

		callback = _onClipboardClearNeeded
		syncLock.unlock()

		callback?()
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
