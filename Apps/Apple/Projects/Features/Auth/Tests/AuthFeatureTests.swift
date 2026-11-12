import Foundation
import Testing
@testable import AuthFeatureImplementation
import AuthFeatureInterface
import AuthFeatureTesting
import KeyManageDomain

// MARK: - Mocks

@MainActor
private final class MockAuthPresenter: AuthPresentable {
	var isLoading: Bool = false
	var errorMessage: String?
	var clearInputsCallCount: Int = 0

	func setLoading(_ isLoading: Bool) {
		self.isLoading = isLoading
	}

	func setErrorMessage(_ message: String?) {
		self.errorMessage = message
	}

	func clearInputs() {
		clearInputsCallCount += 1
	}
}

private final class MockLockManagerForAuth: VaultLockManagerProtocol, @unchecked Sendable {
	var isLocked: Bool = true
	var lastInteractionTime: Date = Date()
	var email: String?
	var sessionToken: String?
	var salt: Data?
	var iterations: Int = 600_000
	var encryptedVault: EncryptedVaultPayload?
	var revision: Int = 0
	var masterKey: Data?
	var encryptionKey: Data?
	var autoLockTimeout: AutoLockTimeout = .fiveMinutes
	var isPrivacyCoverActive: Bool = false
	var hasActiveSession: Bool = false
	var hasPendingSync: Bool = false
	var isOffline: Bool = false
	var isSyncing: Bool = false
	var decryptedVault: VaultData?
	var isBiometricsEnabled: Bool = false
	var lastMasterPasswordUnlockDate: Date?
	var currentBiometryStateHash: Data?
	var onClipboardClearNeeded: (@Sendable () -> Void)?

	var loginCallCount: Int = 0
	var registerCallCount: Int = 0
	var shouldThrowError: Error?

	func checkIdleTimeout(at date: Date) -> Bool { false }
	func didEnterBackground(at date: Date) {}
	func willEnterForeground(at date: Date) {}
	func lock() { isLocked = true }
	func unlockWithMasterPassword(password: String, now: Date) throws {}
	func unlockWithBiometrics(evaluatedStateHash: Data, recoveredEncryptionKey: Data, now: Date) throws {}
	func logout() { hasActiveSession = false; isLocked = true }
	func setupSession(email: String, sessionToken: String, salt: Data, iterations: Int, encryptedVault: EncryptedVaultPayload, isBiometricsEnabled: Bool, biometryStateHash: Data?, revision: Int) {}
	func recordInteraction(at date: Date) {}
	func exportBackup(now: Date) throws -> (fileName: String, data: Data) { ("", Data()) }
	func restoreBackup(fileData: Data, password: String, now: Date) throws -> (restoredCount: Int, newVault: VaultData) { (0, VaultData(updatedAt: "", items: [])) }
	func changeMasterPassword(currentPassword: String, newPassword: String, networkClient: AuthNetworkClientProtocol, now: Date) async throws {}
	func deleteAccount(password: String, confirmationPhrase: String, networkClient: AuthNetworkClientProtocol, now: Date) async throws {}
	func checkIfPasswordChangedOnServer(networkClient: AuthNetworkClientProtocol) async throws -> Bool { false }
	func addVaultItem(serviceName: String, username: String, password: String, url: String?, notes: String?, now: Date) throws -> VaultItem {
		let timeString = ISO8601DateFormatter().string(from: now)
		return VaultItem(id: "test", serviceName: serviceName, username: username, password: password, url: url, notes: notes, createdAt: timeString, updatedAt: timeString)
	}
	func updateVaultItem(item: VaultItem, now: Date) throws {}
	func deleteVaultItem(id: String, now: Date) throws {}

	func register(email: String, password: String, confirmation: String, networkClient: AuthNetworkClientProtocol, now: Date) async throws {
		registerCallCount += 1
		if let error = shouldThrowError { throw error }
		hasActiveSession = true
		isLocked = false
	}

	func login(email: String, password: String, networkClient: AuthNetworkClientProtocol, now: Date) async throws {
		loginCallCount += 1
		if let error = shouldThrowError { throw error }
		hasActiveSession = true
		isLocked = false
	}

	func sync(networkClient: AuthNetworkClientProtocol, now: Date) async throws -> (vault: VaultData, revision: Int) {
		return (decryptedVault ?? VaultData(updatedAt: "", items: []), 1)
	}

	func setOffline(_ isOffline: Bool) {
		self.isOffline = isOffline
	}
}

private final class DummyAuthNetworkClient: AuthNetworkClientProtocol, @unchecked Sendable {
	func prelogin(email: String) async throws -> PreloginResponse {
		PreloginResponse(kdf: "PBKDF2", salt: "c2FsdA==", iterations: 600000)
	}
	func register(request: RegisterRequestPayload) async throws -> RegisterResponsePayload {
		RegisterResponsePayload(accessToken: "tok", expiresAt: "2026-11-27T11:20:00Z")
	}
	func login(request: LoginRequestPayload) async throws -> LoginResponsePayload {
		LoginResponsePayload(accessToken: "tok", expiresAt: "2026-11-27T11:20:00Z")
	}
	func getVault(accessToken: String) async throws -> GetVaultResponsePayload? { nil }
	func saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload) async throws -> Int { 1 }
	func changePassword(accessToken: String, request: ChangePasswordRequestPayload) async throws -> ChangePasswordResponsePayload {
		ChangePasswordResponsePayload(accessToken: "tok", expiresAt: "2026-11-27T11:20:00Z", revision: 1)
	}
	func deleteAccount(accessToken: String, authHash: String) async throws {}
}

// MARK: - Tests

@Suite("AuthFeature Unit Tests")
struct AuthFeatureTests {

	@Test("AuthViewModel: 모드 전환 시 비밀번호 및 에러 초기화")
	@MainActor
	func viewModel_switchMode_resetsInputsAndErrors() {
		let vm = AuthViewModel(mode: .login)
		vm.email = "test@example.com"
		vm.password = "secret123456"
		vm.confirmation = "secret123456"
		vm.errorMessage = "Some error"

		vm.switchMode()
		#expect(vm.mode == .register)
		#expect(vm.password.isEmpty)
		#expect(vm.confirmation.isEmpty)
		#expect(vm.errorMessage == nil)
		#expect(vm.email == "test@example.com") // 이메일은 유지

		vm.switchMode()
		#expect(vm.mode == .login)
	}

	@Test("AuthViewModel: 로그인/회원가입 폼 유효성 검사")
	@MainActor
	func viewModel_canSubmitValidation() {
		let vm = AuthViewModel(mode: .login)

		// 로그인 모드: 이메일과 비밀번호가 있으면 제출 가능
		#expect(!vm.canSubmit)
		vm.email = "user@test.com"
		#expect(!vm.canSubmit)
		vm.password = "any"
		#expect(vm.canSubmit)

		// 회원가입 모드: 이메일 + 비밀번호 12자 이상 + 확인 일치
		vm.mode = .register
		vm.password = "short"
		vm.confirmation = "short"
		#expect(!vm.canSubmit) // 12자 미만

		vm.password = "twelve_chars_long"
		vm.confirmation = "mismatching_confirmation"
		#expect(!vm.canSubmit) // 불일치

		vm.confirmation = "twelve_chars_long"
		#expect(vm.canSubmit) // 유효

		// 로딩 중에는 제출 불가
		vm.isLoading = true
		#expect(!vm.canSubmit)
	}

	@Test("AuthViewState: 강도 게이지는 회원가입 모드에서 비밀번호가 있을 때만 보인다 (SEC-002)")
	func viewState_strengthGauge_visibility() {
		#expect(!AuthViewState(mode: .register, password: "").showsStrengthGauge)
		#expect(!AuthViewState(mode: .login, password: "Abcdefgh12").showsStrengthGauge)
		#expect(AuthViewState(mode: .register, password: "a").showsStrengthGauge)
	}

	@Test("AuthViewState: 59점은 취약 경고, 60점은 경고 없음 (SEC-002, GEN-001 경계값)")
	func viewState_weakWarning_boundary() {
		let score59 = AuthViewState(mode: .register, password: "Abcdefgh12")
		#expect(score59.passwordStrengthScore == 59)
		#expect(score59.showsWeakPasswordWarning)

		let score60 = AuthViewState(mode: .register, password: "abcdefghijk한")
		#expect(score60.passwordStrengthScore == 60)
		#expect(!score60.showsWeakPasswordWarning)

		#expect(!AuthViewState(mode: .login, password: "Abcdefgh12").showsWeakPasswordWarning)
		#expect(!AuthViewState(mode: .register, password: "").showsWeakPasswordWarning)
	}

	@Test("AuthViewState: 취약한 비밀번호도 가입은 막지 않는다 (SEC-002)")
	func viewState_weakPassword_stillSubmittable() {
		let weak = "aaaaaaaaaaaa"
		let state = AuthViewState(mode: .register, email: "user@test.com", password: weak, confirmation: weak)
		#expect(state.showsWeakPasswordWarning)
		#expect(state.canSubmit)
	}

	@Test("AuthStrings: 가입 비밀번호 취약 문구 (SEC-002)")
	func strings_weakPasswordWarning() {
		#expect(AuthStrings.weakMasterPasswordWarning == "마스터 비밀번호가 보안에 취약합니다. 더 길게 만들거나 여러 종류의 문자를 섞어 주세요.")
	}

	@Test("AuthInteractor: 로그인 성공 시 didUnlock 호출 및 입력 초기화")
	@MainActor
	func interactor_loginSuccess_triggersListener() async {
		let lockManager = MockLockManagerForAuth()
		let listener = MockAuthListener()
		let presenter = MockAuthPresenter()
		let client = DummyAuthNetworkClient()

		let interactor = AuthInteractor(
			lockManager: lockManager,
			networkClient: client,
			presenter: presenter
		)
		interactor.listener = listener

		interactor.didRequestLogin(email: "user@test.com", password: "mypassword1234")

		try? await Task.sleep(nanoseconds: 50_000_000)

		#expect(lockManager.loginCallCount == 1)
		#expect(listener.authDidCompleteCallCount == 1)
		#expect(presenter.clearInputsCallCount == 1)
		#expect(presenter.errorMessage == nil)
	}

	@Test("AuthInteractor: 로그인 실패 시 에러 메시지 표시")
	@MainActor
	func interactor_loginFailure_displaysErrorMessage() async {
		let lockManager = MockLockManagerForAuth()
		lockManager.shouldThrowError = AuthError.loginFailed
		let listener = MockAuthListener()
		let presenter = MockAuthPresenter()
		let client = DummyAuthNetworkClient()

		let interactor = AuthInteractor(
			lockManager: lockManager,
			networkClient: client,
			presenter: presenter
		)
		interactor.listener = listener

		interactor.didRequestLogin(email: "user@test.com", password: "wrongpassword")

		try? await Task.sleep(nanoseconds: 50_000_000)

		#expect(lockManager.loginCallCount == 1)
		#expect(listener.authDidCompleteCallCount == 0)
		#expect(presenter.errorMessage == AuthStrings.loginFailedMessage)
	}

	@Test("AuthInteractor: 회원가입 성공 시 didUnlock 호출")
	@MainActor
	func interactor_registerSuccess_triggersListener() async {
		let lockManager = MockLockManagerForAuth()
		let listener = MockAuthListener()
		let presenter = MockAuthPresenter()
		let client = DummyAuthNetworkClient()

		let interactor = AuthInteractor(
			lockManager: lockManager,
			networkClient: client,
			presenter: presenter
		)
		interactor.listener = listener

		interactor.didRequestRegister(
			email: "new@test.com",
			password: "valid_master_pw_123",
			confirmation: "valid_master_pw_123"
		)

		try? await Task.sleep(nanoseconds: 50_000_000)

		#expect(lockManager.registerCallCount == 1)
		#expect(listener.authDidCompleteCallCount == 1)
		#expect(presenter.clearInputsCallCount == 1)
	}
}
