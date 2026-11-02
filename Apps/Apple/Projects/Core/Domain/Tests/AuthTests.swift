import Foundation
import Testing
@testable import KeyManageDomain

// MARK: - Test Doubles (MockAuthNetworkClient)

private final class MockAuthNetworkClient: AuthNetworkClientProtocol, @unchecked Sendable {
	var registerCallCount: Int = 0
	var loginCallCount: Int = 0
	var preloginCallCount: Int = 0
	var getVaultCallCount: Int = 0
	var changePasswordCallCount: Int = 0
	var deleteAccountCallCount: Int = 0

	var receivedRegisterPayload: RegisterRequestPayload?
	var receivedLoginPayload: LoginRequestPayload?
	var receivedPreloginEmail: String?
	var receivedGetVaultAccessToken: String?
	var receivedChangePasswordAccessToken: String?
	var receivedChangePasswordRequest: ChangePasswordRequestPayload?
	var receivedDeleteAccountAccessToken: String?
	var receivedDeleteAccountAuthHash: String?

	var preloginResponseToReturn: PreloginResponse?
	var registerResponseToReturn: RegisterResponsePayload?
	var loginResponseToReturn: LoginResponsePayload?
	var getVaultResponseToReturn: GetVaultResponsePayload?
	var changePasswordResponseToReturn: ChangePasswordResponsePayload?

	var shouldThrowError: Error?
	var preloginError: Error?
	var registerError: Error?
	var loginError: Error?
	var getVaultError: Error?

	static let defaultSalt = Data([
		0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
		0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
	])

	func prelogin(email: String) async throws -> PreloginResponse {
		preloginCallCount += 1
		receivedPreloginEmail = email

		if let error = preloginError ?? shouldThrowError {
			throw error
		}

		if let response = preloginResponseToReturn {
			return response
		}

		return PreloginResponse(
			kdf: "PBKDF2-HMAC-SHA256",
			salt: Self.defaultSalt.base64EncodedString(),
			iterations: PBKDF2KeyDeriver.defaultIterations
		)
	}

	func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload {
		registerCallCount += 1
		receivedRegisterPayload = request

		if let error = registerError ?? shouldThrowError {
			throw error
		}

		if let response = registerResponseToReturn {
			return response
		}

		return RegisterResponsePayload(
			accessToken: "access_token_reg_test_99999",
			expiresAt: "2026-11-27T11:20:00Z"
		)
	}

	func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
		loginCallCount += 1
		receivedLoginPayload = request

		if let error = loginError ?? shouldThrowError {
			throw error
		}

		if let response = loginResponseToReturn {
			return response
		}

		return LoginResponsePayload(
			accessToken: "access_token_login_test_88888",
			expiresAt: "2026-11-27T11:20:00Z"
		)
	}

	func getVault(accessToken: String) async throws -> GetVaultResponsePayload? {
		getVaultCallCount += 1
		receivedGetVaultAccessToken = accessToken

		if let error = getVaultError ?? shouldThrowError {
			throw error
		}

		return getVaultResponseToReturn
	}

	func changePassword(
		accessToken: String,
		request: ChangePasswordRequestPayload
	) async throws -> ChangePasswordResponsePayload {
		changePasswordCallCount += 1
		receivedChangePasswordAccessToken = accessToken
		receivedChangePasswordRequest = request

		if let error = shouldThrowError {
			throw error
		}

		if let response = changePasswordResponseToReturn {
			return response
		}

		return ChangePasswordResponsePayload(
			accessToken: "dummy_new_token",
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
}

// MARK: - Test Doubles (MockAuthURLProtocol)

private final class MockAuthURLProtocol: URLProtocol, @unchecked Sendable {
	nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

	override class func canInit(with request: URLRequest) -> Bool {
		true
	}

	override class func canonicalRequest(for request: URLRequest) -> URLRequest {
		request
	}

	override func startLoading() {
		guard let handler = MockAuthURLProtocol.requestHandler else {
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

private func makeMockURLSessionNetworkClient() -> URLSessionAuthNetworkClient {
	let config = URLSessionConfiguration.ephemeral
	config.protocolClasses = [MockAuthURLProtocol.self]
	let session = URLSession(configuration: config)
	return URLSessionAuthNetworkClient(
		baseURL: URL(string: "https://api.keymanage.local/api/v1")!,
		session: session
	)
}

// MARK: - Helpers

private func makeEncryptedVault(
	masterPassword: String,
	salt: Data,
	iterations: Int = PBKDF2KeyDeriver.defaultIterations,
	revision: Int = 5,
	items: [VaultItem] = [
		VaultItem(
			id: "660e8400-e29b-41d4-a716-446655440001",
			serviceName: "GitHub",
			username: "octocat",
			password: "SuperSecretPassword123!",
			createdAt: "2026-11-02T10:00:00Z",
			updatedAt: "2026-11-02T12:00:00Z"
		)
	]
) throws -> (vault: VaultData, payload: EncryptedVaultPayload) {
	let normalized = try PasswordPreprocessor.preprocess(masterPassword)
	let masterKey = try PBKDF2KeyDeriver.deriveMasterKey(
		password: normalized,
		salt: salt,
		iterations: iterations
	)
	let separatedKeys = try HKDFKeySeparator.separate(masterKey: masterKey)

	let testVault = VaultData(
		version: 1,
		updatedAt: "2026-11-02T12:00:00Z",
		items: items
	)
	let encoded = try JSONEncoder().encode(testVault)
	let encrypted = try VaultCipher.encrypt(plaintext: encoded, key: separatedKeys.encryptionKey)
	return (vault: testVault, payload: encrypted)
}

// MARK: - AuthTests Suite

@Suite("AuthTests - SEC-002 Apple 계정 인증 및 세션 단위 테스트")
struct AuthTests {

	// MARK: - 1. 회원가입 (Registration) 테스트

	@Suite("1. 회원가입 (Registration)")
	struct RegistrationTests {

		@Test("SEC-002: 올바른 입력으로 회원가입 시 이메일 정규화, PBKDF2+HKDF 키 유도, 서버 등록 및 빈 볼트 세션 활성화")
		func register_withValidInputs_succeedsAndActivatesSession() async throws {
			// Given
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let emailInput = "  Test.User@KeyManage.IO  "
			let expectedNormalizedEmail = "test.user@keymanage.io"
			let password = "ValidMasterPassword123!"
			let confirmation = "ValidMasterPassword123!"
			let now = Date(timeIntervalSince1970: 1793534400)

			#expect(manager.hasActiveSession == false)
			#expect(manager.sessionToken == nil)

			// When
			try await manager.register(
				email: emailInput,
				password: password,
				confirmation: confirmation,
				networkClient: mockClient,
				now: now
			)

			// Then: 서버 호출 및 페이로드 검증
			#expect(mockClient.registerCallCount == 1)
			let payload = try #require(mockClient.receivedRegisterPayload)
			#expect(payload.email == expectedNormalizedEmail)
			#expect(payload.kdfIterations == 600_000)

			// 16바이트 salt 검증
			let saltData = try #require(Data(base64Encoded: payload.kdfSalt))
			#expect(saltData.count == 16)

			// 32바이트 authHash 검증
			let authHashData = try #require(Data(base64Encoded: payload.authHash))
			#expect(authHashData.count == 32)

			// 로컬 세션 활성화 검증
			#expect(manager.hasActiveSession == true)
			#expect(manager.isLocked == false)
			#expect(manager.email == expectedNormalizedEmail)
			#expect(manager.sessionToken == "access_token_reg_test_99999")
			#expect(manager.salt == saltData)
			#expect(manager.iterations == 600_000)
			#expect(manager.revision == 1)

			// 빈 볼트 초기화 검증
			let vault = try #require(manager.decryptedVault)
			#expect(vault.items.isEmpty == true)
			#expect(vault.version == 1)
			#expect(manager.encryptedVault != nil)
		}

		@Test("SEC-002: 이메일 형식 유효성 검증 실패 시 AuthError.invalidEmail 발생 및 네트워크 호출 차단")
		func register_withInvalidEmail_throwsInvalidEmail() async {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let validPassword = "MasterPassword123!"

			let invalidEmails = [
				"",                       // 빈 문자열
				"   \t\n  ",              // 공백 문자열
				"plainaddress",           // @ 누락
				"user@",                  // 도메인 누락
				"@example.com",           // 로컬 파트 누락
				"user@example",           // TLD (.dot) 누락
				"user space@example.com", // 공백 포함
				String(repeating: "a", count: 245) + "@domain.com" // 254자 초과
			]

			for email in invalidEmails {
				await #expect(throws: AuthError.invalidEmail) {
					try await manager.register(
						email: email,
						password: validPassword,
						confirmation: validPassword,
						networkClient: mockClient
					)
				}
			}

			#expect(mockClient.registerCallCount == 0)
			#expect(manager.hasActiveSession == false)
		}

		@Test("SEC-002: 이메일 최대 길이 254자 경계값 검증 (254자 성공, 255자 실패)")
		func register_emailLengthBoundary_validates254Limit() async throws {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let password = "MasterPassword123!"

			// 254자 생성: 241자 로컬 + "@keymanage.io" (13자) = 254자
			let local241 = String(repeating: "u", count: 241)
			let email254 = "\(local241)@keymanage.io"
			#expect(email254.count == 254)

			try await manager.register(
				email: email254,
				password: password,
				confirmation: password,
				networkClient: mockClient
			)
			#expect(mockClient.registerCallCount == 1)

			// 255자 생성: 242자 로컬 + "@keymanage.io" (13자) = 255자
			let local242 = String(repeating: "u", count: 242)
			let email255 = "\(local242)@keymanage.io"
			#expect(email255.count == 255)

			await #expect(throws: AuthError.invalidEmail) {
				try await manager.register(
					email: email255,
					password: password,
					confirmation: password,
					networkClient: mockClient
				)
			}
			#expect(mockClient.registerCallCount == 1) // 255자 호출은 서버로 전달되지 않음
		}

		@Test("SEC-002: 마스터 비밀번호 길이 검증 (11자 실패 AuthError.passwordTooShort, 12자 성공)")
		func register_passwordLengthBoundary_validates12CharacterMinimum() async throws {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let validEmail = "user@keymanage.local"

			// 11자: passwordTooShort 에러
			let shortPassword = "ShortPass11"
			#expect(shortPassword.count == 11)

			await #expect(throws: AuthError.passwordTooShort) {
				try await manager.register(
					email: validEmail,
					password: shortPassword,
					confirmation: shortPassword,
					networkClient: mockClient
				)
			}
			#expect(mockClient.registerCallCount == 0)

			// 12자: 성공
			let exact12Password = "Pass12Chars!"
			#expect(exact12Password.count == 12)

			try await manager.register(
				email: validEmail,
				password: exact12Password,
				confirmation: exact12Password,
				networkClient: mockClient
			)
			#expect(mockClient.registerCallCount == 1)
			#expect(manager.hasActiveSession == true)
		}

		@Test("SEC-002: 마스터 비밀번호와 확인 입력 불일치 시 AuthError.confirmationMismatch 발생")
		func register_confirmationMismatch_throwsConfirmationMismatch() async {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()

			await #expect(throws: AuthError.confirmationMismatch) {
				try await manager.register(
					email: "user@keymanage.local",
					password: "MasterPassword123!",
					confirmation: "MasterPassword123?",
					networkClient: mockClient
				)
			}

			// 대소문자 불일치
			await #expect(throws: AuthError.confirmationMismatch) {
				try await manager.register(
					email: "user@keymanage.local",
					password: "MasterPassword123!",
					confirmation: "masterpassword123!",
					networkClient: mockClient
				)
			}

			#expect(mockClient.registerCallCount == 0)
			#expect(manager.hasActiveSession == false)
		}

		@Test("SEC-002: 서버 가입 에러 전파 (409 중복, 403 가입 꺼짐, 429 시도 초과)")
		func register_serverErrors_mappedCorrectly() async {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let email = "user@keymanage.local"
			let password = "MasterPassword123!"

			// 409 EMAIL_ALREADY_REGISTERED
			mockClient.registerError = AuthError.emailAlreadyRegistered
			await #expect(throws: AuthError.emailAlreadyRegistered) {
				try await manager.register(
					email: email,
					password: password,
					confirmation: password,
					networkClient: mockClient
				)
			}

			// 403 REGISTRATION_DISABLED
			mockClient.registerError = AuthError.registrationDisabled
			await #expect(throws: AuthError.registrationDisabled) {
				try await manager.register(
					email: email,
					password: password,
					confirmation: password,
					networkClient: mockClient
				)
			}

			// 429 TOO_MANY_ATTEMPTS
			mockClient.registerError = AuthError.tooManyAttempts
			await #expect(throws: AuthError.tooManyAttempts) {
				try await manager.register(
					email: email,
					password: password,
					confirmation: password,
					networkClient: mockClient
				)
			}

			#expect(manager.hasActiveSession == false)
		}
	}

	// MARK: - 2. 로그인 (Login) 테스트

	@Suite("2. 로그인 (Login)")
	struct LoginTests {

		@Test("SEC-002: 올바른 자격증명으로 로그인 시 prelogin -> 키 유도 -> login -> getVault 복호화 및 세션 활성화")
		func login_withValidCredentialsAndExistingVault_succeedsAndRestoresVault() async throws {
			// Given
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let email = "  Login.User@KeyManage.Local  "
			let expectedNormalizedEmail = "login.user@keymanage.local"
			let password = "SecretMasterPassword123!"
			let salt = MockAuthNetworkClient.defaultSalt
			let iterations = 600_000
			let revision = 12
			let now = Date(timeIntervalSince1970: 1793535000)

			let items = [
				VaultItem(
					id: "item-001",
					serviceName: "Google",
					username: "user@gmail.com",
					password: "GooglePassword123!",
					createdAt: "2026-11-02T10:00:00Z",
					updatedAt: "2026-11-02T12:00:00Z"
				),
				VaultItem(
					id: "item-002",
					serviceName: "AWS",
					username: "admin",
					password: "AwsPassword123!",
					createdAt: "2026-11-02T10:00:00Z",
					updatedAt: "2026-11-02T12:00:00Z"
				)
			]

			let encryptedFixture = try makeEncryptedVault(
				masterPassword: password,
				salt: salt,
				iterations: iterations,
				revision: revision,
				items: items
			)

			mockClient.getVaultResponseToReturn = GetVaultResponsePayload(
				revision: revision,
				payload: encryptedFixture.payload
			)

			#expect(manager.hasActiveSession == false)

			// When
			try await manager.login(
				email: email,
				password: password,
				networkClient: mockClient,
				now: now
			)

			// Then: prelogin 및 login 호출 검증
			#expect(mockClient.preloginCallCount == 1)
			#expect(mockClient.receivedPreloginEmail == expectedNormalizedEmail)

			#expect(mockClient.loginCallCount == 1)
			let loginPayload = try #require(mockClient.receivedLoginPayload)
			#expect(loginPayload.email == expectedNormalizedEmail)
			let authHashData = try #require(Data(base64Encoded: loginPayload.authHash))
			#expect(authHashData.count == 32)

			// getVault 호출 및 복호화 검증
			#expect(mockClient.getVaultCallCount == 1)
			#expect(mockClient.receivedGetVaultAccessToken == "access_token_login_test_88888")

			// 로컬 세션 활성화 및 볼트 아이템 검증
			#expect(manager.hasActiveSession == true)
			#expect(manager.isLocked == false)
			#expect(manager.email == expectedNormalizedEmail)
			#expect(manager.sessionToken == "access_token_login_test_88888")
			#expect(manager.salt == salt)
			#expect(manager.iterations == iterations)
			#expect(manager.revision == revision)

			let decryptedVault = try #require(manager.decryptedVault)
			#expect(decryptedVault.items.count == 2)
			#expect(decryptedVault.items[0].serviceName == "Google")
			#expect(decryptedVault.items[1].serviceName == "AWS")
		}

		@Test("SEC-002: 원격 볼트가 없는 계정(404 nil) 로그인 시 빈 볼트로 초기화 및 revision 1 세션 활성화")
		func login_whenRemoteVaultIsNil_initializesEmptyVault() async throws {
			// Given
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			mockClient.getVaultResponseToReturn = nil // 원격 볼트 없음 (404)

			// When
			try await manager.login(
				email: "newuser@keymanage.local",
				password: "ValidPassword123!",
				networkClient: mockClient
			)

			// Then
			#expect(mockClient.preloginCallCount == 1)
			#expect(mockClient.loginCallCount == 1)
			#expect(mockClient.getVaultCallCount == 1)

			#expect(manager.hasActiveSession == true)
			#expect(manager.isLocked == false)
			#expect(manager.revision == 1)
			let vault = try #require(manager.decryptedVault)
			#expect(vault.items.isEmpty == true)
			#expect(manager.encryptedVault != nil)
		}

		@Test("SEC-002: KDF 반복 횟수 경계값 검증 (600,000 미만 또는 10,000,000 초과 시 AuthError.loginFailed 거절)")
		func login_kdfIterationsBounds_enforcesLimits() async {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let email = "user@keymanage.local"
			let password = "ValidPassword123!"

			// 599,999 회: 거절
			mockClient.preloginResponseToReturn = PreloginResponse(
				kdf: "PBKDF2-HMAC-SHA256",
				salt: MockAuthNetworkClient.defaultSalt.base64EncodedString(),
				iterations: 599_999
			)

			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}
			#expect(mockClient.loginCallCount == 0)

			// 10,000,001 회: 기기 멈춤 방지를 위해 거절
			mockClient.preloginResponseToReturn = PreloginResponse(
				kdf: "PBKDF2-HMAC-SHA256",
				salt: MockAuthNetworkClient.defaultSalt.base64EncodedString(),
				iterations: 10_000_001
			)

			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}
			#expect(mockClient.loginCallCount == 0)
			#expect(manager.hasActiveSession == false)
		}

		@Test("SEC-002: salt 길이가 16바이트가 아니거나 유효하지 않은 base64인 경우 AuthError.loginFailed 거절")
		func login_invalidSalt_throwsLoginFailed() async {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()

			// 15바이트 salt
			let shortSalt = Data(repeating: 0x01, count: 15).base64EncodedString()
			mockClient.preloginResponseToReturn = PreloginResponse(
				kdf: "PBKDF2-HMAC-SHA256",
				salt: shortSalt,
				iterations: 600_000
			)

			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: "user@keymanage.local",
					password: "ValidPassword123!",
					networkClient: mockClient
				)
			}

			// 32바이트 salt
			let longSalt = Data(repeating: 0x01, count: 32).base64EncodedString()
			mockClient.preloginResponseToReturn = PreloginResponse(
				kdf: "PBKDF2-HMAC-SHA256",
				salt: longSalt,
				iterations: 600_000
			)

			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: "user@keymanage.local",
					password: "ValidPassword123!",
					networkClient: mockClient
				)
			}

			// 손상된 base64
			mockClient.preloginResponseToReturn = PreloginResponse(
				kdf: "PBKDF2-HMAC-SHA256",
				salt: "Not@@Valid@@Base64!!",
				iterations: 600_000
			)

			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: "user@keymanage.local",
					password: "ValidPassword123!",
					networkClient: mockClient
				)
			}

			#expect(mockClient.loginCallCount == 0)
		}

		@Test("SEC-002: 원격 볼트 복호화 실패 시(틀린 비밀번호/페이로드 손상) AuthError.loginFailed 발생 및 세션 미활성화")
		func login_vaultDecryptionFailure_throwsLoginFailed() async throws {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let email = "user@keymanage.local"
			let correctPassword = "CorrectPassword123!"

			// 다른 키(비밀번호)로 암호화된 볼트
			let differentFixture = try makeEncryptedVault(
				masterPassword: "DifferentPassword999!",
				salt: MockAuthNetworkClient.defaultSalt
			)
			mockClient.getVaultResponseToReturn = GetVaultResponsePayload(
				revision: 3,
				payload: differentFixture.payload
			)

			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: email,
					password: correctPassword,
					networkClient: mockClient
				)
			}

			#expect(manager.hasActiveSession == false)
			#expect(manager.decryptedVault == nil)
		}

		@Test("SEC-002: 서버 로그인 에러 전파 (401 실패, 429 시도 초과, 네트워크 단절 offline)")
		func login_serverErrors_mappedCorrectly() async {
			let manager = VaultLockManager()
			let mockClient = MockAuthNetworkClient()
			let email = "user@keymanage.local"
			let password = "ValidPassword123!"

			// prelogin 401 (없는 이메일 또는 실패)
			mockClient.preloginError = AuthError.loginFailed
			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}

			// login 401 (틀린 authHash)
			mockClient.preloginError = nil
			mockClient.loginError = AuthError.loginFailed
			await #expect(throws: AuthError.loginFailed) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}

			// 429 시도 초과
			mockClient.loginError = AuthError.tooManyAttempts
			await #expect(throws: AuthError.tooManyAttempts) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}

			// offline 연결 불가
			mockClient.loginError = AuthError.offline
			await #expect(throws: AuthError.offline) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}

			// getVault 세션 만료 401
			mockClient.loginError = nil
			mockClient.getVaultError = AuthError.sessionExpired
			await #expect(throws: AuthError.sessionExpired) {
				try await manager.login(
					email: email,
					password: password,
					networkClient: mockClient
				)
			}

			#expect(manager.hasActiveSession == false)
		}
	}

	// MARK: - 3. 정책 표준 문구 (Policy Strings) 테스트

	@Suite("3. 정책 표준 문구 (Policy Strings)")
	struct PolicyStringsTests {

		@Test("SEC-002: AuthStrings 상수가 정책 표의 사용자 문구와 글자 하나까지 완전 일치 검증")
		func authStrings_matchPolicyTableVerbatim() {
			#expect(AuthStrings.loginFailedMessage == "이메일 또는 마스터 비밀번호가 올바르지 않습니다.")
			#expect(AuthStrings.sessionExpiredMessage == "로그인이 만료되었습니다. 다시 로그인해 주세요.")
			#expect(AuthStrings.tooManyAttemptsMessage == "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.")
			#expect(AuthStrings.emailAlreadyRegisteredMessage == "이미 가입된 이메일입니다.")
			#expect(AuthStrings.registrationDisabledMessage == "현재 새 계정을 만들 수 없습니다.")
			#expect(AuthStrings.passwordTooShortMessage == "마스터 비밀번호는 12자 이상이어야 합니다.")
			#expect(AuthStrings.confirmationMismatchMessage == "비밀번호 확인이 일치하지 않습니다.")
			#expect(AuthStrings.invalidEmailFormatMessage == "올바른 이메일 주소를 입력해 주세요.")
			#expect(AuthStrings.invalidCharacterMessage == "마스터 비밀번호에 쓸 수 없는 문자가 있습니다.")
			#expect(AuthStrings.serverConnectionFailedMessage == "서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.")

			// UI 레이블 검증
			#expect(AuthStrings.loginTitle == "로그인")
			#expect(AuthStrings.registerTitle == "회원가입")
			#expect(AuthStrings.emailPlaceholder == "이메일 주소")
			#expect(AuthStrings.passwordPlaceholder == "마스터 비밀번호 (12자 이상)")
			#expect(AuthStrings.confirmationPlaceholder == "비밀번호 확인")
			#expect(AuthStrings.switchToRegisterPrompt == "계정이 없으신가요? 회원가입")
			#expect(AuthStrings.switchToLoginPrompt == "이미 계정이 있으신가요? 로그인")
		}

		@Test("SEC-002: AuthError 열거형의 errorDescription이 해당 정책 문구를 정확히 반환")
		func authError_errorDescription_matchesPolicyStrings() {
			#expect(AuthError.invalidEmail.errorDescription == AuthStrings.invalidEmailFormatMessage)
			#expect(AuthError.passwordTooShort.errorDescription == AuthStrings.passwordTooShortMessage)
			#expect(AuthError.confirmationMismatch.errorDescription == AuthStrings.confirmationMismatchMessage)
			#expect(AuthError.invalidCharacter.errorDescription == AuthStrings.invalidCharacterMessage)
			#expect(AuthError.loginFailed.errorDescription == AuthStrings.loginFailedMessage)
			#expect(AuthError.sessionExpired.errorDescription == AuthStrings.sessionExpiredMessage)
			#expect(AuthError.emailAlreadyRegistered.errorDescription == AuthStrings.emailAlreadyRegisteredMessage)
			#expect(AuthError.registrationDisabled.errorDescription == AuthStrings.registrationDisabledMessage)
			#expect(AuthError.tooManyAttempts.errorDescription == AuthStrings.tooManyAttemptsMessage)
			#expect(AuthError.offline.errorDescription == AuthStrings.serverConnectionFailedMessage)

			let customNetworkError = AuthError.networkError("사용자 지정 네트워크 오류")
			#expect(customNetworkError.errorDescription == "사용자 지정 네트워크 오류")

			let customOtherError = AuthError.other("기타 내부 오류")
			#expect(customOtherError.errorDescription == "기타 내부 오류")
		}
	}

	// MARK: - 4. URLSessionAuthNetworkClient HTTP 상태 코드 매핑 테스트

	@Suite("4. URLSessionAuthNetworkClient HTTP 상태 코드 매핑", .serialized)
	struct URLSessionAuthNetworkClientTests {

		@Test("api-spec 2.5.1: POST /auth/prelogin 200 응답 역직렬화 및 401/404/429/오프라인 에러 매핑 검증")
		func prelogin_httpStatusCodes() async throws {
			let client = makeMockURLSessionNetworkClient()

			// 200 OK
			MockAuthURLProtocol.requestHandler = { request in
				#expect(request.url?.path.hasSuffix("/auth/prelogin") == true)
				#expect(request.httpMethod == "POST")
				let responseJSON = """
				{
					"kdf": "PBKDF2-HMAC-SHA256",
					"salt": "c2FsdDEyMzQ1Njc4OTAxMg==",
					"iterations": 600000
				}
				""".data(using: .utf8)!
				let response = HTTPURLResponse(
					url: request.url!,
					statusCode: 200,
					httpVersion: nil,
					headerFields: ["Content-Type": "application/json"]
				)!
				return (response, responseJSON)
			}
			let result = try await client.prelogin(email: "user@example.com")
			#expect(result.kdf == "PBKDF2-HMAC-SHA256")
			#expect(result.iterations == 600000)

			// 401 Unauthorized -> AuthError.loginFailed
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 401, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.loginFailed) {
				_ = try await client.prelogin(email: "user@example.com")
			}

			// 404 Not Found -> AuthError.loginFailed
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 404, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.loginFailed) {
				_ = try await client.prelogin(email: "notfound@example.com")
			}

			// 429 Too Many Requests -> AuthError.tooManyAttempts
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 429, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.tooManyAttempts) {
				_ = try await client.prelogin(email: "user@example.com")
			}

			// Offline error
			MockAuthURLProtocol.requestHandler = { _ in
				throw URLError(.notConnectedToInternet)
			}
			await #expect(throws: AuthError.offline) {
				_ = try await client.prelogin(email: "user@example.com")
			}
		}

		@Test("api-spec 2.5.2: POST /auth/register 201 응답 역직렬화 및 409/403/429/오프라인 에러 매핑 검증")
		func register_httpStatusCodes() async throws {
			let client = makeMockURLSessionNetworkClient()
			let dummyPayload = RegisterRequestPayload(
				email: "user@example.com",
				authHash: "dGhpcyBpcyAzMiBieXRlcyBhdXRoIGhhc2g=",
				kdfSalt: "dGhpcyBpcyAxNiBzYWx0",
				kdfIterations: 600_000
			)

			// 201 Created
			MockAuthURLProtocol.requestHandler = { request in
				#expect(request.url?.path.hasSuffix("/auth/register") == true)
				#expect(request.httpMethod == "POST")
				let responseJSON = """
				{
					"accessToken": "created_access_token_123",
					"expiresAt": "2026-11-27T11:20:00Z"
				}
				""".data(using: .utf8)!
				let response = HTTPURLResponse(
					url: request.url!,
					statusCode: 201,
					httpVersion: nil,
					headerFields: ["Content-Type": "application/json"]
				)!
				return (response, responseJSON)
			}
			let result = try await client.register(request: dummyPayload)
			#expect(result.accessToken == "created_access_token_123")

			// 409 Conflict -> AuthError.emailAlreadyRegistered
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 409, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.emailAlreadyRegistered) {
				try await client.register(request: dummyPayload)
			}

			// 403 Forbidden -> AuthError.registrationDisabled
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 403, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.registrationDisabled) {
				try await client.register(request: dummyPayload)
			}

			// 429 Too Many Requests -> AuthError.tooManyAttempts
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 429, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.tooManyAttempts) {
				try await client.register(request: dummyPayload)
			}

			// URLError.notConnectedToInternet -> AuthError.offline
			MockAuthURLProtocol.requestHandler = { _ in
				throw URLError(.notConnectedToInternet)
			}
			await #expect(throws: AuthError.offline) {
				try await client.register(request: dummyPayload)
			}
		}

		@Test("api-spec 2.5.3: POST /auth/login 200 응답 역직렬화 및 401/429/오프라인 에러 매핑 검증")
		func login_httpStatusCodes() async throws {
			let client = makeMockURLSessionNetworkClient()
			let dummyPayload = LoginRequestPayload(
				email: "user@example.com",
				authHash: "dGhpcyBpcyAzMiBieXRlcyBhdXRoIGhhc2g="
			)

			// 200 OK
			MockAuthURLProtocol.requestHandler = { request in
				#expect(request.url?.path.hasSuffix("/auth/login") == true)
				#expect(request.httpMethod == "POST")
				let responseJSON = """
				{
					"accessToken": "login_access_token_456",
					"expiresAt": "2026-11-27T11:20:00Z"
				}
				""".data(using: .utf8)!
				let response = HTTPURLResponse(
					url: request.url!,
					statusCode: 200,
					httpVersion: nil,
					headerFields: ["Content-Type": "application/json"]
				)!
				return (response, responseJSON)
			}
			let result = try await client.login(request: dummyPayload)
			#expect(result.accessToken == "login_access_token_456")

			// 401 Unauthorized -> AuthError.loginFailed
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 401, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.loginFailed) {
				try await client.login(request: dummyPayload)
			}

			// 429 Too Many Requests -> AuthError.tooManyAttempts
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 429, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.tooManyAttempts) {
				try await client.login(request: dummyPayload)
			}

			// URLError.networkConnectionLost -> AuthError.offline
			MockAuthURLProtocol.requestHandler = { _ in
				throw URLError(.networkConnectionLost)
			}
			await #expect(throws: AuthError.offline) {
				try await client.login(request: dummyPayload)
			}
		}

		@Test("api-spec 2.3: GET /vault 200, 404 nil, 401 세션만료 에러 매핑 검증")
		func getVault_httpStatusCodes() async throws {
			let client = makeMockURLSessionNetworkClient()

			// 200 OK
			MockAuthURLProtocol.requestHandler = { request in
				#expect(request.url?.path.hasSuffix("/vault") == true)
				#expect(request.httpMethod == "GET")
				#expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer valid_token_999")

				let responseJSON = """
				{
					"revision": 7,
					"payload": {
						"version": 1,
						"cipher": "AES-256-GCM",
						"ciphertext": "YWJjZGVm",
						"iv": "MTIzNDU2Nzg5MDEy",
						"tag": "dGFnMTIzNDU2Nzg5MDEy"
					}
				}
				""".data(using: .utf8)!
				let response = HTTPURLResponse(
					url: request.url!,
					statusCode: 200,
					httpVersion: nil,
					headerFields: ["Content-Type": "application/json"]
				)!
				return (response, responseJSON)
			}
			let result = try await client.getVault(accessToken: "valid_token_999")
			let vaultPayload = try #require(result)
			#expect(vaultPayload.revision == 7)
			#expect(vaultPayload.payload.ciphertext == "YWJjZGVm")

			// 404 Not Found -> nil 반환
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 404, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			let emptyResult = try await client.getVault(accessToken: "valid_token_999")
			#expect(emptyResult == nil)

			// 401 Unauthorized -> AuthError.sessionExpired
			MockAuthURLProtocol.requestHandler = { request in
				let response = HTTPURLResponse(url: request.url!, statusCode: 401, httpVersion: nil, headerFields: nil)!
				return (response, Data())
			}
			await #expect(throws: AuthError.sessionExpired) {
				try await client.getVault(accessToken: "expired_token")
			}
		}
	}
}
