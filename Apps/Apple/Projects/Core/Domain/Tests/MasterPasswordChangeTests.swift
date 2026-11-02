import Foundation
import Testing
@testable import KeyManageDomain

// MARK: - Test Doubles (MockPasswordChangeNetworkClient)

private final class MockPasswordChangeNetworkClient: AuthNetworkClientProtocol, @unchecked Sendable {
	var preloginCallCount: Int = 0
	var changePasswordCallCount: Int = 0
	var deleteAccountCallCount: Int = 0

	var receivedPreloginEmail: String?
	var receivedAccessToken: String?
	var receivedRequestPayload: ChangePasswordRequestPayload?
	var receivedDeleteAccountAccessToken: String?
	var receivedDeleteAccountAuthHash: String?

	var preloginResponseToReturn: PreloginResponse?
	var changePasswordResponseToReturn: ChangePasswordResponsePayload?
	var shouldThrowError: Error?

	init(
		preloginResponseToReturn: PreloginResponse? = nil,
		changePasswordResponseToReturn: ChangePasswordResponsePayload? = nil,
		shouldThrowError: Error? = nil
	) {
		self.preloginResponseToReturn = preloginResponseToReturn
		self.changePasswordResponseToReturn = changePasswordResponseToReturn
		self.shouldThrowError = shouldThrowError
	}

	func prelogin(email: String) async throws -> PreloginResponse {
		preloginCallCount += 1
		receivedPreloginEmail = email

		if let error = shouldThrowError {
			throw error
		}

		if let response = preloginResponseToReturn {
			return response
		}

		return PreloginResponse(
			kdf: "PBKDF2",
			salt: Data(repeating: 0x01, count: 16).base64EncodedString(),
			iterations: PBKDF2KeyDeriver.defaultIterations
		)
	}

	func changePassword(
		accessToken: String,
		request: ChangePasswordRequestPayload
	) async throws -> ChangePasswordResponsePayload {
		changePasswordCallCount += 1
		receivedAccessToken = accessToken
		receivedRequestPayload = request

		if let error = shouldThrowError {
			throw error
		}

		if let response = changePasswordResponseToReturn {
			return response
		}

		return ChangePasswordResponsePayload(
			accessToken: "new_session_token_67890",
			expiresAt: "2026-11-03T12:00:00Z",
			revision: request.vault.expectedRevision + 1
		)
	}

	func deleteAccount(
		accessToken: String,
		authHash: String
	) async throws {
		deleteAccountCallCount += 1
		receivedDeleteAccountAccessToken = accessToken
		receivedDeleteAccountAuthHash = authHash

		if let error = shouldThrowError {
			throw error
		}
	}

	func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload {
		if let error = shouldThrowError { throw error }
		return RegisterResponsePayload(accessToken: "test_token", expiresAt: "2026-11-27T11:20:00Z")
	}

	func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
		if let error = shouldThrowError { throw error }
		return LoginResponsePayload(accessToken: "test_token", expiresAt: "2026-11-27T11:20:00Z")
	}

	func getVault(accessToken: String) async throws -> GetVaultResponsePayload? {
		if let error = shouldThrowError { throw error }
		return nil
	}
}

// MARK: - Fixture Helpers

private struct TestFixture {
	let masterPassword: String
	let newPassword: String
	let email: String
	let sessionToken: String
	let salt: Data
	let iterations: Int
	let revision: Int
	let encryptedVault: EncryptedVaultPayload
	let decryptedVault: VaultData
}

private func makeFixture(
	isReadOnly: Bool = false,
	iterations: Int = PBKDF2KeyDeriver.defaultIterations
) throws -> TestFixture {
	let masterPassword = "CurrentMasterPassword123!"
	let newPassword = "NewMasterPassword456!"
	let email = "user@keymanage.local"
	let sessionToken = "session_token_original_12345"
	let salt = Data([
		0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
		0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
	])
	let revision = 3

	let normalized = try PasswordPreprocessor.preprocess(masterPassword)
	let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
		password: normalized,
		salt: salt,
		iterations: iterations
	)
	let encryptionKey = try HKDFKeySeparator.deriveEncryptionKey(masterKey: masterKey)

	let testVault = VaultData(
		version: isReadOnly ? VaultData.currentVersion + 1 : 1,
		updatedAt: "2026-11-02T10:00:00Z",
		items: [
			VaultItem(
				id: "550e8400-e29b-41d4-a716-446655440000",
				serviceName: "GitHub",
				username: "octocat",
				password: "SamplePassword456!",
				createdAt: "2026-11-02T09:00:00Z",
				updatedAt: "2026-11-02T10:00:00Z"
			)
		],
		isReadOnly: isReadOnly
	)
	let plaintext = try JSONEncoder().encode(testVault)
	let encryptedVault = try VaultCipher.encrypt(plaintext: plaintext, key: encryptionKey)

	return TestFixture(
		masterPassword: masterPassword,
		newPassword: newPassword,
		email: email,
		sessionToken: sessionToken,
		salt: salt,
		iterations: iterations,
		revision: revision,
		encryptedVault: encryptedVault,
		decryptedVault: testVault
	)
}

private func makeUnlockedLockManager(
	fixture: TestFixture,
	now: Date = Date()
) throws -> VaultLockManager {
	let manager = VaultLockManager()
	manager.setupSession(
		email: fixture.email,
		sessionToken: fixture.sessionToken,
		salt: fixture.salt,
		iterations: fixture.iterations,
		encryptedVault: fixture.encryptedVault,
		isBiometricsEnabled: false,
		biometryStateHash: nil,
		revision: fixture.revision
	)
	try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: now)
	return manager
}

// MARK: - MasterPasswordChangeTests Suite

@Suite("MasterPasswordChangeTests - 마스터 비밀번호 변경 및 KDF 감지 단위 테스트")
struct MasterPasswordChangeTests {

	// MARK: - 1. SEC-004 마스터 비밀번호 변경 성공 테스트

	@Test("SEC-004: 마스터 비밀번호 변경 성공 시 원격 요청 전송 및 로컬 자격증명이 갱신되어 새 비밀번호로 잠금 해제 가능")
	func changeMasterPassword_success_updatesCredentialsAndAllowsUnlockWithNewPassword() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient(
			changePasswordResponseToReturn: ChangePasswordResponsePayload(
				accessToken: "new_session_token_77777",
				expiresAt: "2026-11-03T12:00:00Z",
				revision: fixture.revision + 1
			)
		)
		let changeDate = Date(timeIntervalSince1970: 2000)

		// When
		try await manager.changeMasterPassword(
			currentPassword: fixture.masterPassword,
			newPassword: fixture.newPassword,
			networkClient: mockClient,
			now: changeDate
		)

		// Then
		#expect(mockClient.changePasswordCallCount == 1)
		#expect(mockClient.receivedAccessToken == fixture.sessionToken)

		let request = try #require(mockClient.receivedRequestPayload)
		let decodedSalt = try #require(Data(base64Encoded: request.kdf.salt))
		#expect(decodedSalt.count == 16)
		#expect(request.vault.expectedRevision == fixture.revision)
		#expect(request.kdf.iterations == fixture.iterations)

		#expect(manager.sessionToken == "new_session_token_77777")
		#expect(manager.salt == decodedSalt)
		#expect(manager.salt != fixture.salt)
		#expect(manager.revision == fixture.revision + 1)

		// 잠금 후 새 마스터 비밀번호로 잠금 해제 검증
		manager.lock()
		#expect(manager.isLocked == true)

		try manager.unlockWithMasterPassword(password: fixture.newPassword, now: changeDate.addingTimeInterval(10))
		#expect(manager.isLocked == false)
		#expect(manager.decryptedVault?.items.count == 1)
		#expect(manager.decryptedVault?.items.first?.serviceName == "GitHub")

		// 이전 비밀번호로 잠금 해제 시도시 거절 검증
		manager.lock()
		#expect(throws: LockError.invalidMasterPassword) {
			try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: changeDate.addingTimeInterval(20))
		}
	}

	// MARK: - 2. SEC-004 거절 / 예외 시나리오 테스트

	@Test("SEC-004: 현재 비밀번호가 올바르지 않으면 invalidCurrentPassword를 던지고 원격 요청하지 않음")
	func changeMasterPassword_withWrongCurrentPassword_throwsInvalidCurrentPassword() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient()

		// When & Then
		await #expect(throws: MasterPasswordChangeError.invalidCurrentPassword) {
			try await manager.changeMasterPassword(
				currentPassword: "WrongPassword999!",
				newPassword: fixture.newPassword,
				networkClient: mockClient
			)
		}
		#expect(mockClient.changePasswordCallCount == 0)
	}

	@Test("SEC-004: 새 비밀번호가 12자 미만이면 tooShort를 던지고 원격 요청하지 않음")
	func changeMasterPassword_withPasswordShorterThan12Chars_throwsTooShort() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient()
		let shortPassword = "Short12345!" // 11 characters

		// When & Then
		await #expect(throws: MasterPasswordChangeError.tooShort) {
			try await manager.changeMasterPassword(
				currentPassword: fixture.masterPassword,
				newPassword: shortPassword,
				networkClient: mockClient
			)
		}
		#expect(mockClient.changePasswordCallCount == 0)
	}

	@Test("SEC-004: 새 비밀번호가 현재 비밀번호와 동일하면 samePassword를 던지고 원격 요청하지 않음")
	func changeMasterPassword_withSamePassword_throwsSamePassword() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient()

		// When & Then
		await #expect(throws: MasterPasswordChangeError.samePassword) {
			try await manager.changeMasterPassword(
				currentPassword: fixture.masterPassword,
				newPassword: fixture.masterPassword,
				networkClient: mockClient
			)
		}
		#expect(mockClient.changePasswordCallCount == 0)
	}

	@Test("SEC-004: 볼트가 잠금 상태이면 offline을 던지고 원격 요청하지 않음")
	func changeMasterPassword_whenVaultIsLocked_throwsOffline() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient()
		manager.lock()

		// When & Then
		await #expect(throws: MasterPasswordChangeError.offline) {
			try await manager.changeMasterPassword(
				currentPassword: fixture.masterPassword,
				newPassword: fixture.newPassword,
				networkClient: mockClient
			)
		}
		#expect(mockClient.changePasswordCallCount == 0)
	}

	@Test("SEC-004: 세션 정보가 없는 미초기화 상태이면 offline을 던지고 원격 요청하지 않음")
	func changeMasterPassword_whenSessionUninitialized_throwsOffline() async throws {
		// Given
		let fixture = try makeFixture()
		let uninitializedManager = VaultLockManager()
		let mockClient = MockPasswordChangeNetworkClient()

		// When & Then
		await #expect(throws: MasterPasswordChangeError.offline) {
			try await uninitializedManager.changeMasterPassword(
				currentPassword: fixture.masterPassword,
				newPassword: fixture.newPassword,
				networkClient: mockClient
			)
		}
		#expect(mockClient.changePasswordCallCount == 0)
	}

	@Test("SEC-004: 볼트가 읽기 전용(isReadOnly = true)인 경우 readOnlyVault를 던지고 원격 요청하지 않음")
	func changeMasterPassword_whenVaultIsReadOnly_throwsReadOnlyVault() async throws {
		// Given
		let fixture = try makeFixture(isReadOnly: true)
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient()

		// When & Then
		await #expect(throws: MasterPasswordChangeError.readOnlyVault) {
			try await manager.changeMasterPassword(
				currentPassword: fixture.masterPassword,
				newPassword: fixture.newPassword,
				networkClient: mockClient
			)
		}
		#expect(mockClient.changePasswordCallCount == 0)
	}

	@Test("SEC-004: 네트워크 통신 에러 발생 시 에러가 전파되고 기존 자격증명이 변경되지 않음")
	func changeMasterPassword_whenNetworkFails_propagatesErrorAndPreservesCredentials() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let networkError = MasterPasswordChangeError.networkError("네트워크 타임아웃 발생")
		let mockClient = MockPasswordChangeNetworkClient(shouldThrowError: networkError)

		// When & Then
		await #expect(throws: MasterPasswordChangeError.networkError("네트워크 타임아웃 발생")) {
			try await manager.changeMasterPassword(
				currentPassword: fixture.masterPassword,
				newPassword: fixture.newPassword,
				networkClient: mockClient
			)
		}

		#expect(mockClient.changePasswordCallCount == 1)
		#expect(manager.sessionToken == fixture.sessionToken)
		#expect(manager.salt == fixture.salt)
		#expect(manager.revision == fixture.revision)

		// 기존 마스터 비밀번호로 계속 정상 잠금 및 잠금 해제 가능한지 확인
		manager.lock()
		#expect(manager.isLocked == true)
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: Date())
		#expect(manager.isLocked == false)
	}

	// MARK: - 3. SEC-003 규칙 11 서버 KDF 변경 감지 테스트

	@Test("SEC-003: 서버의 salt가 로컬 salt와 다르면 checkIfPasswordChangedOnServer가 true 반환")
	func checkIfPasswordChangedOnServer_whenSaltDiffers_returnsTrue() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let differentSalt = Data([
			0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x00, 0x11,
			0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99
		])
		let mockClient = MockPasswordChangeNetworkClient(
			preloginResponseToReturn: PreloginResponse(
				kdf: "PBKDF2",
				salt: differentSalt.base64EncodedString(),
				iterations: fixture.iterations
			)
		)

		// When
		let changed = try await manager.checkIfPasswordChangedOnServer(networkClient: mockClient)

		// Then
		#expect(changed == true)
		#expect(mockClient.preloginCallCount == 1)
		#expect(mockClient.receivedPreloginEmail == fixture.email)
	}

	@Test("SEC-003: 서버의 iterations가 로컬 iterations와 다르면 checkIfPasswordChangedOnServer가 true 반환")
	func checkIfPasswordChangedOnServer_whenIterationsDiffer_returnsTrue() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient(
			preloginResponseToReturn: PreloginResponse(
				kdf: "PBKDF2",
				salt: fixture.salt.base64EncodedString(),
				iterations: fixture.iterations + 100_000
			)
		)

		// When
		let changed = try await manager.checkIfPasswordChangedOnServer(networkClient: mockClient)

		// Then
		#expect(changed == true)
		#expect(mockClient.preloginCallCount == 1)
		#expect(mockClient.receivedPreloginEmail == fixture.email)
	}

	@Test("SEC-003: 서버의 salt와 iterations가 로컬과 완전히 동일하면 checkIfPasswordChangedOnServer가 false 반환")
	func checkIfPasswordChangedOnServer_whenSaltAndIterationsMatch_returnsFalse() async throws {
		// Given
		let fixture = try makeFixture()
		let manager = try makeUnlockedLockManager(fixture: fixture)
		let mockClient = MockPasswordChangeNetworkClient(
			preloginResponseToReturn: PreloginResponse(
				kdf: "PBKDF2",
				salt: fixture.salt.base64EncodedString(),
				iterations: fixture.iterations
			)
		)

		// When
		let changed = try await manager.checkIfPasswordChangedOnServer(networkClient: mockClient)

		// Then
		#expect(changed == false)
		#expect(mockClient.preloginCallCount == 1)
		#expect(mockClient.receivedPreloginEmail == fixture.email)
	}

	@Test("SEC-003: 세션 정보가 없으면 prelogin 호출 없이 false 반환")
	func checkIfPasswordChangedOnServer_whenNoSession_returnsFalse() async throws {
		// Given
		let uninitializedManager = VaultLockManager()
		let mockClient = MockPasswordChangeNetworkClient()

		// When
		let changed = try await uninitializedManager.checkIfPasswordChangedOnServer(networkClient: mockClient)

		// Then
		#expect(changed == false)
		#expect(mockClient.preloginCallCount == 0)
	}

	// MARK: - 4. SEC-004 및 SEC-003 정책 표준 문구 검증

	@Test("SEC-004 & SEC-003: 서비스 정책 표준 안내 및 에러 문구 불변식 검증")
	func policyStrings_matchStandardExactMessages() {
		// Given & When & Then
		#expect(
			MasterPasswordChangeStrings.noticeBody ==
			"비밀번호를 바꾸면 다른 기기는 모두 로그아웃되고, 새 비밀번호로 다시 로그인해야 합니다. 다른 기기에서 아직 올리지 못한 변경 사항은 사라집니다. 예전에 내보낸 백업은 예전 비밀번호로 엽니다."
		)
		#expect(MasterPasswordChangeStrings.samePasswordMessage == "새 비밀번호가 지금 비밀번호와 같습니다.")
		#expect(MasterPasswordChangeStrings.offlineMessage == "인터넷에 연결된 상태에서만 비밀번호를 바꿀 수 있습니다.")
		#expect(MasterPasswordChangeStrings.completedMessage == "마스터 비밀번호를 바꿨습니다.")
		#expect(MasterPasswordChangeStrings.passwordChangedReLoginNotice == "마스터 비밀번호가 바뀌었습니다. 새 비밀번호로 로그인해 주세요.")
	}

	@Test("SEC-004 & SEC-003: MasterPasswordChangeError LocalizedError 설명이 정책 표준 문구와 일치 검증")
	func errorDescriptions_matchPolicyStrings() {
		// Given & When & Then
		#expect(MasterPasswordChangeError.samePassword.errorDescription == MasterPasswordChangeStrings.samePasswordMessage)
		#expect(MasterPasswordChangeError.offline.errorDescription == MasterPasswordChangeStrings.offlineMessage)
		#expect(MasterPasswordChangeError.tooShort.errorDescription == MasterPasswordChangeStrings.tooShortMessage)
		#expect(MasterPasswordChangeError.invalidCurrentPassword.errorDescription == MasterPasswordChangeStrings.invalidCurrentPasswordMessage)
		#expect(MasterPasswordChangeError.conflict.errorDescription == MasterPasswordChangeStrings.conflictMessage)
	}
}
