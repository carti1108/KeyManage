import Foundation
import Testing
@testable import KeyManageDomain

// MARK: - Test Doubles (MockAccountDeletionClient)

private final class MockAccountDeletionClient: AuthNetworkClientProtocol, @unchecked Sendable {
	var preloginCallCount: Int = 0
	var changePasswordCallCount: Int = 0
	var deleteAccountCallCount: Int = 0

	var receivedDeleteAccountAccessToken: String?
	var receivedDeleteAccountAuthHash: String?

	var shouldThrowError: Error?

	init(shouldThrowError: Error? = nil) {
		self.shouldThrowError = shouldThrowError
	}

	func prelogin(email: String) async throws -> PreloginResponse {
		preloginCallCount += 1
		if let error = shouldThrowError {
			throw error
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
		if let error = shouldThrowError {
			throw error
		}
		return ChangePasswordResponsePayload(
			accessToken: "dummy_token",
			expiresAt: "2026-11-03T12:00:00Z",
			revision: 1
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
		return RegisterResponsePayload(accessToken: "dummy_token", expiresAt: "2026-11-27T11:20:00Z")
	}

	func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
		if let error = shouldThrowError { throw error }
		return LoginResponsePayload(accessToken: "dummy_token", expiresAt: "2026-11-27T11:20:00Z")
	}

	func getVault(accessToken: String) async throws -> GetVaultResponsePayload? {
		if let error = shouldThrowError { throw error }
		return nil
	}
}

// MARK: - Test Doubles (MockURLProtocol for URLSessionAuthNetworkClient)

private final class MockURLProtocol: URLProtocol, @unchecked Sendable {
	nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

	override class func canInit(with request: URLRequest) -> Bool {
		true
	}

	override class func canonicalRequest(for request: URLRequest) -> URLRequest {
		request
	}

	override func startLoading() {
		guard let handler = MockURLProtocol.requestHandler else {
			client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
			return
		}
		do {
			let (response, data) = try handler(request)
			client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
			client?.urlProtocol(self, didLoad: data)
			client?.urlProtocolDidFinishLoading(self)
		} catch {
			client?.urlProtocol(self, didFailWithError: error)
		}
	}

	override func stopLoading() {}
}

// MARK: - Test Fixture

private struct AccountDeletionFixture {
	let masterPassword: String
	let email: String
	let sessionToken: String
	let salt: Data
	let iterations: Int
	let revision: Int
	let encryptedVault: EncryptedVaultPayload
	let decryptedVault: VaultData
	let expectedAuthHashBase64: String
}

private func makeDeletionFixture(
	isReadOnly: Bool = false,
	iterations: Int = PBKDF2KeyDeriver.defaultIterations
) throws -> AccountDeletionFixture {
	let masterPassword = "MasterPassword123!"
	let email = "user@keymanage.local"
	let sessionToken = "session_token_valid_99999"
	let salt = Data([
		0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,
		0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x2E, 0x2F
	])
	let revision = 5

	let normalized = try PasswordPreprocessor.preprocess(masterPassword)
	let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
		password: normalized,
		salt: salt,
		iterations: iterations
	)
	let separatedKeys = try HKDFKeySeparator.separate(masterKey: masterKey)
	let expectedAuthHashBase64 = separatedKeys.authHash.base64EncodedString()

	let testVault = VaultData(
		version: isReadOnly ? (VaultData.currentVersion + 1) : 1,
		updatedAt: "2026-11-02T12:00:00Z",
		items: [
			VaultItem(
				id: "660e8400-e29b-41d4-a716-446655440001",
				serviceName: "Google",
				username: "tester",
				password: "SecretPassword123!",
				createdAt: "2026-11-02T10:00:00Z",
				updatedAt: "2026-11-02T12:00:00Z"
			)
		],
		isReadOnly: isReadOnly
	)
	let plaintext = try JSONEncoder().encode(testVault)
	let encryptedVault = try VaultCipher.encrypt(plaintext: plaintext, key: separatedKeys.encryptionKey)

	return AccountDeletionFixture(
		masterPassword: masterPassword,
		email: email,
		sessionToken: sessionToken,
		salt: salt,
		iterations: iterations,
		revision: revision,
		encryptedVault: encryptedVault,
		decryptedVault: testVault,
		expectedAuthHashBase64: expectedAuthHashBase64
	)
}

private func makeUnlockedManager(
	fixture: AccountDeletionFixture,
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

private func makeMockURLSessionNetworkClient() -> URLSessionAuthNetworkClient {
	let config = URLSessionConfiguration.ephemeral
	config.protocolClasses = [MockURLProtocol.self]
	let session = URLSession(configuration: config)
	return URLSessionAuthNetworkClient(
		baseURL: URL(string: "https://api.keymanage.local/api/v1")!,
		session: session
	)
}

// MARK: - AccountDeletionTests Suite

@Suite("AccountDeletionTests - SEC-005 Apple 계정 삭제 도메인 단위 테스트")
struct AccountDeletionTests {

	// MARK: - 1. 성공 시나리오

	@Test("SEC-005: 올바른 마스터 비밀번호와 확인 문구로 계정 삭제 성공 시 서버 요청 전송 및 로컬 세션 소각")
	func deleteAccount_withValidCredentials_callsServerAndLogsOutLocally() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient()

		#expect(manager.isLocked == false)
		#expect(manager.sessionToken == fixture.sessionToken)
		#expect(manager.email == fixture.email)
		#expect(manager.decryptedVault != nil)

		// When
		try await manager.deleteAccount(
			password: fixture.masterPassword,
			confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
			networkClient: mockClient
		)

		// Then: 서버 호출 검증 (올바른 세션 토큰 및 AuthHash 전달)
		#expect(mockClient.deleteAccountCallCount == 1)
		#expect(mockClient.receivedDeleteAccountAccessToken == fixture.sessionToken)
		#expect(mockClient.receivedDeleteAccountAuthHash == fixture.expectedAuthHashBase64)

		// Then: 로컬 세션 소각 검증 (lockManager.logout() 실행 완료)
		#expect(manager.isLocked == true)
		#expect(manager.sessionToken == nil)
		#expect(manager.email == nil)
		#expect(manager.salt == nil)
		#expect(manager.decryptedVault == nil)
	}

	@Test("SEC-005: 읽기 전용 볼트(isReadOnly == true) 상태에서도 SEC-005 규칙 1에 따라 계정 삭제가 성공하고 로컬 세션 소각")
	func deleteAccount_whenVaultIsReadOnly_succeedsAndLogsOut() async throws {
		// Given: 읽기 전용 볼트
		let fixture = try makeDeletionFixture(isReadOnly: true)
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient()

		#expect(manager.decryptedVault?.isReadOnly == true)
		#expect(manager.isLocked == false)

		// When
		try await manager.deleteAccount(
			password: fixture.masterPassword,
			confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
			networkClient: mockClient
		)

		// Then: 읽기 전용 볼트라도 정상적으로 삭제 요청 전송 및 로컬 소각
		#expect(mockClient.deleteAccountCallCount == 1)
		#expect(mockClient.receivedDeleteAccountAccessToken == fixture.sessionToken)
		#expect(mockClient.receivedDeleteAccountAuthHash == fixture.expectedAuthHashBase64)
		#expect(manager.isLocked == true)
		#expect(manager.sessionToken == nil)
		#expect(manager.email == nil)
	}

	// MARK: - 2. 확인 문구 검증 (SEC-005 값)

	@Test(
		"SEC-005: 확인 문구 불일치(공백 누락, 앞뒤 공백, 철자 오류 등) 시 confirmationMismatch 에러 발생 및 서버 요청 차단",
		arguments: [
			"계정삭제",       // 공백 없음
			" 계정 삭제",      // 앞 공백
			"계정 삭제 ",      // 뒤 공백
			" 계정 삭제 ",     // 앞뒤 공백
			"delete",         // 영문
			"계정",           // 축약
			"삭제",           // 축약
			"",               // 빈 문자열
			"계정  삭제",      // 중간 2개 공백
			"계정 삭제\n"      // 개행 포함
		]
	)
	func deleteAccount_withInvalidConfirmationPhrase_throwsConfirmationMismatch(phrase: String) async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient()

		// When & Then
		await #expect(throws: AccountDeletionError.confirmationMismatch) {
			try await manager.deleteAccount(
				password: fixture.masterPassword,
				confirmationPhrase: phrase,
				networkClient: mockClient
			)
		}

		// Then: 원격 요청 없음 및 볼트 언락 상태 유지
		#expect(mockClient.deleteAccountCallCount == 0)
		#expect(manager.isLocked == false)
		#expect(manager.sessionToken == fixture.sessionToken)
	}

	// MARK: - 3. 마스터 비밀번호 검증

	@Test("SEC-005: 마스터 비밀번호 불일치 시 invalidPassword 에러를 로컬에서 발생시키고 서버 요청 차단")
	func deleteAccount_withWrongPassword_throwsInvalidPasswordLocally() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient()

		// When & Then
		await #expect(throws: AccountDeletionError.invalidPassword) {
			try await manager.deleteAccount(
				password: "IncorrectPassword999!",
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		// Then: 로컬 검증 실패이므로 서버 요청이 발생하지 않고 세션 보존
		#expect(mockClient.deleteAccountCallCount == 0)
		#expect(manager.isLocked == false)
		#expect(manager.sessionToken == fixture.sessionToken)
	}

	@Test("SEC-005: 빈 마스터 비밀번호 입력 시 invalidPassword 에러 발생 및 서버 요청 차단")
	func deleteAccount_withEmptyPassword_throwsInvalidPassword() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient()

		// When & Then
		await #expect(throws: AccountDeletionError.invalidPassword) {
			try await manager.deleteAccount(
				password: "",
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		#expect(mockClient.deleteAccountCallCount == 0)
		#expect(manager.isLocked == false)
	}

	// MARK: - 4. 오프라인 및 상태 검증 (SEC-005 규칙 1)

	@Test("SEC-005: 볼트가 잠겨있는(isLocked == true) 상태에서 계정 삭제 호출 시 vaultLocked 에러 발생")
	func deleteAccount_whenVaultIsLocked_throwsVaultLocked() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient()
		manager.lock()

		#expect(manager.isLocked == true)

		// When & Then
		await #expect(throws: AccountDeletionError.vaultLocked) {
			try await manager.deleteAccount(
				password: fixture.masterPassword,
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		#expect(mockClient.deleteAccountCallCount == 0)
	}

	@Test("SEC-005: 세션 토큰이 없는 오프라인 상태에서 계정 삭제 호출 시 offline 에러 발생")
	func deleteAccount_whenSessionTokenMissing_throwsOffline() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = VaultLockManager()
		manager.setupSession(
			email: fixture.email,
			sessionToken: nil, // 세션 토큰 없음
			salt: fixture.salt,
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault,
			isBiometricsEnabled: false,
			biometryStateHash: nil,
			revision: fixture.revision
		)
		try manager.unlockWithMasterPassword(password: fixture.masterPassword, now: Date())
		let mockClient = MockAccountDeletionClient()

		// When & Then
		await #expect(throws: AccountDeletionError.offline) {
			try await manager.deleteAccount(
				password: fixture.masterPassword,
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		#expect(mockClient.deleteAccountCallCount == 0)
	}

	@Test("SEC-005: salt가 없는 오프라인 상태에서 계정 삭제 호출 시 offline 에러 발생")
	func deleteAccount_whenSaltMissing_throwsOffline() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = VaultLockManager()
		manager.setupSession(
			email: fixture.email,
			sessionToken: fixture.sessionToken,
			salt: nil, // salt 없음
			iterations: fixture.iterations,
			encryptedVault: fixture.encryptedVault,
			isBiometricsEnabled: false,
			biometryStateHash: nil,
			revision: fixture.revision
		)
		// salt가 없으면 unlock이 실패하므로 세션 불완전 상태
		let mockClient = MockAccountDeletionClient()

		// When & Then: salt가 없는 불완전 세션에서는 offline 에러 발생
		await #expect(throws: AccountDeletionError.offline) {
			try await manager.deleteAccount(
				password: fixture.masterPassword,
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		#expect(mockClient.deleteAccountCallCount == 0)
	}

	@Test("SEC-005: 세션이 초기화되지 않은 uninitialized 상태에서 계정 삭제 호출 시 offline 에러 발생")
	func deleteAccount_whenUninitialized_throwsOffline() async throws {
		// Given
		let uninitializedManager = VaultLockManager()
		let mockClient = MockAccountDeletionClient()

		// When & Then: 세션 정보가 전혀 없는 상태에서는 offline 에러 발생
		await #expect(throws: AccountDeletionError.offline) {
			try await uninitializedManager.deleteAccount(
				password: "SomePassword123!",
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		#expect(mockClient.deleteAccountCallCount == 0)
	}

	// MARK: - 5. 서버 응답 오류 매핑 (URLSessionAuthNetworkClient)

	@Test("SEC-005 URLSession: 서버에서 401 AUTH_FAILED 반환 시 invalidPassword 에러 매핑 검증")
	func urlSessionAuthNetworkClient_whenServerReturns401AuthFailed_mapsToInvalidPassword() async throws {
		// Given
		let client = makeMockURLSessionNetworkClient()
		let jsonErrorBody = """
		{"code": "AUTH_FAILED", "message": "인증 해시 불일치"}
		""".data(using: .utf8)!

		MockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 401,
				httpVersion: nil,
				headerFields: ["Content-Type": "application/json"]
			)!
			return (response, jsonErrorBody)
		}

		// When & Then
		await #expect(throws: AccountDeletionError.invalidPassword) {
			try await client.deleteAccount(accessToken: "valid_token", authHash: "some_auth_hash")
		}
	}

	@Test("SEC-005 URLSession: 서버에서 401 기타(AUTH_FAILED 아님) 반환 시 sessionExpired 에러 매핑 검증")
	func urlSessionAuthNetworkClient_whenServerReturns401Other_mapsToSessionExpired() async throws {
		// Given
		let client = makeMockURLSessionNetworkClient()
		let jsonErrorBody = """
		{"code": "SESSION_EXPIRED", "message": "세션 만료"}
		""".data(using: .utf8)!

		MockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 401,
				httpVersion: nil,
				headerFields: ["Content-Type": "application/json"]
			)!
			return (response, jsonErrorBody)
		}

		// When & Then
		await #expect(throws: AccountDeletionError.sessionExpired) {
			try await client.deleteAccount(accessToken: "expired_token", authHash: "some_auth_hash")
		}
	}

	@Test("SEC-005 URLSession: 서버에서 429 TOO_MANY_ATTEMPTS 반환 시 tooManyAttempts 에러 매핑 검증")
	func urlSessionAuthNetworkClient_whenServerReturns429_mapsToTooManyAttempts() async throws {
		// Given
		let client = makeMockURLSessionNetworkClient()
		let jsonErrorBody = """
		{"code": "TOO_MANY_ATTEMPTS", "message": "요청 제한 초과"}
		""".data(using: .utf8)!

		MockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 429,
				httpVersion: nil,
				headerFields: ["Content-Type": "application/json"]
			)!
			return (response, jsonErrorBody)
		}

		// When & Then
		await #expect(throws: AccountDeletionError.tooManyAttempts) {
			try await client.deleteAccount(accessToken: "valid_token", authHash: "some_auth_hash")
		}
	}

	@Test("SEC-005 URLSession: 서버에서 500 오류 반환 시 networkError 매핑 검증")
	func urlSessionAuthNetworkClient_whenServerReturns500_mapsToNetworkError() async throws {
		// Given
		let client = makeMockURLSessionNetworkClient()
		let jsonErrorBody = """
		{"code": "INTERNAL_ERROR", "message": "서버 내부 오류"}
		""".data(using: .utf8)!

		MockURLProtocol.requestHandler = { request in
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 500,
				httpVersion: nil,
				headerFields: ["Content-Type": "application/json"]
			)!
			return (response, jsonErrorBody)
		}

		// When & Then
		await #expect(throws: AccountDeletionError.networkError("서버 오류: HTTP 500")) {
			try await client.deleteAccount(accessToken: "valid_token", authHash: "some_auth_hash")
		}
	}

	@Test("SEC-005 URLSession: 네트워크 연결 실패 시 networkError(offlineMessage) 매핑 검증")
	func urlSessionAuthNetworkClient_whenNetworkConnectionFails_mapsToNetworkErrorOffline() async throws {
		// Given
		let client = makeMockURLSessionNetworkClient()

		MockURLProtocol.requestHandler = { _ in
			throw URLError(.notConnectedToInternet)
		}

		// When & Then
		await #expect(throws: AccountDeletionError.offline) {
			try await client.deleteAccount(accessToken: "valid_token", authHash: "some_auth_hash")
		}
	}

	@Test("SEC-005 URLSession: 서버에서 204 No Content 반환 시 성공적으로 완료 검증")
	func urlSessionAuthNetworkClient_whenServerReturns204_succeeds() async throws {
		// Given
		let client = makeMockURLSessionNetworkClient()

		MockURLProtocol.requestHandler = { request in
			#expect(request.httpMethod == "POST")
			#expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer test_access_token")
			let response = HTTPURLResponse(
				url: request.url!,
				statusCode: 204,
				httpVersion: nil,
				headerFields: [:]
			)!
			return (response, Data())
		}

		// When & Then: 예외 없이 정상 종료
		try await client.deleteAccount(accessToken: "test_access_token", authHash: "test_auth_hash")
	}

	@Test("SEC-005 Manager: 서버 통신 실패 시 에러가 전파되고 로컬 세션 보존 검증")
	func deleteAccount_whenNetworkClientThrows_propagatesErrorAndPreservesSession() async throws {
		// Given
		let fixture = try makeDeletionFixture()
		let manager = try makeUnlockedManager(fixture: fixture)
		let mockClient = MockAccountDeletionClient(
			shouldThrowError: AccountDeletionError.networkError("서버 응답 불가")
		)

		// When & Then
		await #expect(throws: AccountDeletionError.networkError("서버 응답 불가")) {
			try await manager.deleteAccount(
				password: fixture.masterPassword,
				confirmationPhrase: AccountDeletionStrings.confirmationPhrase,
				networkClient: mockClient
			)
		}

		// Then: 서버 호출 시도 확인 및 세션 보존(로그아웃되지 않음)
		#expect(mockClient.deleteAccountCallCount == 1)
		#expect(manager.isLocked == false)
		#expect(manager.sessionToken == fixture.sessionToken)
		#expect(manager.email == fixture.email)
	}

	// MARK: - 6. SEC-005 표준 정책 문구 일치 검증

	@Test("SEC-005: 서비스 정책 표준 안내 및 에러 문구 불변식 검증")
	func policyStrings_matchExactStandardMessages() {
		// Given & When & Then: SEC-005 SSOT 일치
		#expect(AccountDeletionStrings.confirmationPhrase == "계정 삭제")
		#expect(AccountDeletionStrings.preNoticeTitle == "계정 삭제")
		#expect(
			AccountDeletionStrings.preNoticeBody ==
			"계정과 볼트가 서버에서 바로 지워지고 되돌릴 수 없습니다. 다른 기기도 모두 로그아웃되고, 아직 올리지 못한 변경 사항은 사라집니다. 필요하면 먼저 백업을 내보내 주세요."
		)
		#expect(AccountDeletionStrings.confirmationPrompt == "확인을 위해 '계정 삭제'를 입력해 주세요.")
		#expect(AccountDeletionStrings.invalidPasswordMessage == "마스터 비밀번호가 올바르지 않습니다.")
		#expect(AccountDeletionStrings.offlineMessage == "인터넷에 연결된 상태에서만 계정을 삭제할 수 있습니다.")
		#expect(AccountDeletionStrings.successMessage == "계정을 삭제했습니다.")
		#expect(AccountDeletionStrings.tooManyAttemptsMessage == "시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.")
		#expect(AccountDeletionStrings.sessionExpiredMessage == "세션이 만료되었습니다. 다시 로그인해 주세요.")
		#expect(AccountDeletionStrings.deviceClearFailedMessage == "계정은 삭제했지만 이 기기에 남은 볼트 기록을 지우지 못했습니다.")
		#expect(AccountDeletionStrings.deleteButtonTitle == "계정 삭제")
		#expect(AccountDeletionStrings.cancelButtonTitle == "취소")
	}

	@Test("SEC-005: AccountDeletionError LocalizedError 설명이 정책 표준 문구와 일치 검증")
	func errorDescriptions_matchPolicyStrings() {
		// Given & When & Then
		#expect(AccountDeletionError.confirmationMismatch.errorDescription == AccountDeletionStrings.confirmationPrompt)
		#expect(AccountDeletionError.invalidPassword.errorDescription == AccountDeletionStrings.invalidPasswordMessage)
		#expect(AccountDeletionError.offline.errorDescription == AccountDeletionStrings.offlineMessage)
		#expect(AccountDeletionError.sessionExpired.errorDescription == AccountDeletionStrings.sessionExpiredMessage)
		#expect(AccountDeletionError.tooManyAttempts.errorDescription == AccountDeletionStrings.tooManyAttemptsMessage)
		#expect(AccountDeletionError.deviceClearFailed.errorDescription == AccountDeletionStrings.deviceClearFailedMessage)
		#expect(AccountDeletionError.networkError("오류 발생").errorDescription == "오류 발생")
		#expect(AccountDeletionError.vaultLocked.errorDescription == "볼트가 잠겨 있어 계정을 삭제할 수 없습니다.")
	}
}
